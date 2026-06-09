# Phase 3 — warm-up: 주기적 ping으로 커넥션 유지

## 핵심 질문

> 서버 설정을 변경할 수 없을 때, 주기적 ping으로 커넥션을 살려두는 방법이 효과가 있는가?

Phase 2에서 서버 keep-alive timeout이 짧으면 client 설정이 소용없다는 걸 확인했다. Phase 3는 서버를 건드리지 않고 **client 단에서 ping을 보내 커넥션을 유지**하는 전략을 검증한다.

---

## 사전 작업

서버 keep-alive를 20s로 설정. 요청 간격(30s)보다 짧아서 서버가 먼저 끊는 상황을 만든다.

```bash
SERVER_TOMCAT_CONNECTION_TIMEOUT=20000 docker compose up -d --build external-api
```

실험 후 원복:
```bash
docker compose up -d --build external-api
```

---

## 시나리오별 설계 의도

### Scenario 5: warm-up-off

**의도**: Phase 2와 동일한 패턴 재현. 서버가 먼저 끊고 ping 없이 방치하면 실패한다는 대조군.

**설정**:
- client `maxIdleTime = 60s`
- server keep-alive = 20s
- 요청 간격 = 30s
- ping 없음

```
30s 경과 후:
  서버: 20s timeout → FIN → 커넥션 종료
  클라이언트: 60s timeout이라 pool에 보관
  → 다음 요청: 죽은 커넥션 재사용 → RST → cold → timeout
```

---

### Scenario 6: warm-up-on

**의도**: 30s 간격 사이에 10s마다 `/ping`을 보내 서버 keep-alive 타이머를 리셋한다. 커넥션이 끊기기 전에 계속 갱신하므로 warm 상태가 유지된다.

**설정**:
- client `maxIdleTime = 60s`
- server keep-alive = 20s
- 요청 간격 = 30s
- ping 간격 = 10s (30s 사이에 ping 3회 발송)

```
ping 흐름:
  0s:  /work 측정 요청
  10s: /ping (서버 keep-alive 타이머 리셋)
  20s: /ping (서버 keep-alive 타이머 리셋)
  30s: /work 측정 요청 → warm 상태 유지 → 성공
```

ping 간격(10s)이 server keep-alive(20s)의 절반이므로 타이머가 만료되기 전에 항상 리셋된다.

---

## 실험 실행

```bash
# Scenario 5: warm-up-off
curl -s -X POST "http://localhost:8080/experiments/warm-up-off?totalRequests=5&intervalMs=30000" | jq

# Scenario 6: warm-up-on
curl -s -X POST "http://localhost:8080/experiments/warm-up-on?totalRequests=5&intervalMs=30000" | jq
```

---

## 실험 결과

### Scenario 5: warm-up-off

```json
{
  "scenario": "WARM_UP_OFF",
  "totalRequests": 5,
  "successCount": 1,
  "timeoutCount": 4,
  "timeoutRate": 0.8,
  "coldConnectionCount": 0,
  "avgLatencyMs": 140.6,
  "p50LatencyMs": 148,
  "p95LatencyMs": 162,
  "p99LatencyMs": 162
}
```

### Scenario 6: warm-up-on

```json
{
  "scenario": "WARM_UP_ON",
  "totalRequests": 5,
  "successCount": 5,
  "timeoutCount": 0,
  "timeoutRate": 0.0,
  "coldConnectionCount": 0,
  "avgLatencyMs": 104.8,
  "p50LatencyMs": 102,
  "p95LatencyMs": 114,
  "p99LatencyMs": 114
}
```

---

## 두 시나리오 비교

| 항목 | warm-up-off | warm-up-on |
|---|---|---|
| successCount | 1 / 5 | 5 / 5 |
| timeoutRate | 0.8 | 0.0 |
| avg latency | 140.6ms | 104.8ms |
| p50 | 148ms | 102ms |

**같은 서버 설정, 같은 client maxIdleTime, 같은 요청 간격**에서 ping 유무만으로 이 차이가 났다.

---

## 결과 해석

### warm-up-off: timeoutRate 0.8

Phase 2의 server-short와 동일한 패턴. pre-warm 직후 첫 요청만 성공하고, 이후는 서버가 먼저 끊은 커넥션에 RST → cold → timeout.

### warm-up-on: timeoutRate 0.0

ping이 10s마다 서버의 keep-alive 타이머를 리셋하면서 30s 간격 동안 커넥션이 살아남는다. 모든 측정 요청이 warm 경로를 탔으며, latency도 안정적(avg 104.8ms).

---

## Phase 3 소결

ping은 서버 설정을 변경할 수 없는 상황에서 커넥션을 유지하는 유효한 대안이다. 단, ping도 비용이 따른다:

- 파일 디스크립터 상시 점유
- 메모리 (connection pool 유지)
- 네트워크 트래픽 (ping마다 패킷 송수신)
- 스케줄러 스레드 상시 실행

warm-up-on의 ping 간격(10s)은 서버 keep-alive(20s)의 절반이다. 더 자주 치더라도 결과는 동일하고 비용만 늘어난다. **최적 ping 간격 = server keep-alive timeout보다 조금 짧은 최솟값.**
