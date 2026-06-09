# Phase 2 — server-short: 서버가 먼저 끊는 경우

## 핵심 질문

> client idle timeout을 충분히 길게 잡아도, 서버 keep-alive timeout이 짧으면 어떻게 되는가?

Phase 1의 long-idle은 서버 기본 설정(60s)이 충분히 길어서 성공했다. 이번에는 **서버가 먼저 커넥션을 끊는** 상황을 만든다.

---

## 사전 작업

Tomcat `connection-timeout`을 2s로 줄여서 external-api를 재시작한다.

```bash
SERVER_TOMCAT_CONNECTION_TIMEOUT=2000 docker compose up -d --build external-api
```

실험 후 원복:
```bash
docker compose up -d --build external-api
```

---

## 설계 의도

```
client maxIdleTime = 60s   (충분히 길다)
server keep-alive  = 2s    (짧다)
요청 간격          = 5s

5s 경과 후:
  client: "아직 60s 안 됐으니까 살아있다" → pool에 보관
  server: "2s 됐으니까 FIN 전송" → 커넥션 종료

다음 요청:
  client가 pool에서 죽은 커넥션 꺼내 재사용 시도
  → 서버로부터 RST 수신 (이미 닫힌 커넥션에 패킷 전송)
  → Reactor Netty: RST 감지 → 커넥션 폐기 → 새 TCP 커넥션으로 재시도
  → 새 TCP = cold 경로 → timeout 초과
```

### RST가 등장하는 이유

서버가 FIN을 보내 커넥션을 정상 종료했는데, client가 그 커넥션으로 데이터를 보내면 서버는 "이미 닫은 커넥션에 뭔가 왔다"고 판단해 RST로 응답한다.

Toxiproxy는 TCP 레벨 프록시라서 RST 패킷을 해석하지 않고 그대로 caller-service까지 전달한다. HTTP 레벨 프록시(nginx 등)를 썼다면 RST가 중간에 흡수됐을 것이다.

---

## 실험 실행

```bash
curl -s -X POST "http://localhost:8080/experiments/server-short?totalRequests=10&intervalMs=5000" | jq
```

---

## 실험 결과

```json
{
  "scenario": "SERVER_SHORT",
  "totalRequests": 10,
  "successCount": 1,
  "timeoutCount": 9,
  "timeoutRate": 0.9,
  "coldConnectionCount": 0,
  "avgLatencyMs": 148.2,
  "p50LatencyMs": 148,
  "p95LatencyMs": 163,
  "p99LatencyMs": 163
}
```

---

## 결과 해석

pre-warm으로 수립한 커넥션을 첫 번째 요청에서 재사용해 1회만 성공. 이후 매 요청마다:

1. 서버는 이미 2s timeout으로 커넥션 닫음 (FIN 전송)
2. client는 pool에 "살아있다"고 보관 중인 커넥션 꺼냄
3. 해당 커넥션으로 HTTP 요청 전송 → 서버로부터 RST 수신
4. Reactor Netty가 RST 감지 → 커넥션 폐기 → 새 TCP 커넥션으로 재시도
5. 새 TCP = cold 경로 → cold penalty 부과 → timeout 초과

timeoutRate 0.9 = 10번 중 9번 실패. 예상과 정확히 일치.

---

## Phase 2 소결

Phase 1의 long-idle(성공)과 완전히 같은 client 설정이지만, **서버 keep-alive timeout만 바꿨더니 timeoutRate가 0.0 → 0.9로 폭등**했다.

keep-alive는 client 설정만으로 완성되지 않는다. 상대방 서버의 timeout도 반드시 확인해야 한다. 외부 API처럼 서버 설정을 알 수 없거나 바꿀 수 없는 경우 별도 대책이 필요하다 → Phase 3에서 검증.
