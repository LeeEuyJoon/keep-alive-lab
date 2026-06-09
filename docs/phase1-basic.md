# Phase 1 — 기본 3종: no-keep-alive / short-idle / long-idle

## 핵심 질문

> client 측 keep-alive 설정만으로 latency와 timeout 발생률에 차이가 나는가?

서버 설정은 건드리지 않고, client의 keep-alive 설정(Connection 헤더, idle timeout)만 다르게 줘서 차이를 확인한다.

---

## 사전 작업

서버를 기본 상태로 실행한다. `SERVER_TOMCAT_CONNECTION_TIMEOUT` 기본값 60000ms.

```bash
docker compose up -d --build
```

---

## 시나리오별 설계 의도

### Scenario 1: no-keep-alive

**의도**: keep-alive가 완전히 없을 때의 baseline. 모든 요청이 cold 경로를 탄다면 100% timeout이 나와야 한다.

**설정**:
- `Connection: close` 헤더를 모든 요청에 추가
- 응답 직후 서버가 TCP 커넥션을 닫음
- 다음 요청은 반드시 새 TCP 커넥션

---

### Scenario 2: short-idle

**의도**: keep-alive는 있지만 client idle timeout이 요청 간격보다 짧은 경우. "keep-alive 설정을 했는데도 no-keep-alive와 같은 결과가 나온다"는 것을 보여주려 함.

**설정**:
- client `maxIdleTime = 2s`
- 요청 간격 = 5s
- 5s idle 경과 시 Reactor Netty가 pool에서 커넥션 제거 → 다음 요청은 새 커넥션

---

### Scenario 3: long-idle

**의도**: client idle timeout이 요청 간격보다 충분히 긴 경우. 커넥션이 pool에 살아있어 재사용된다.

**설정**:
- client `maxIdleTime = 60s`
- 요청 간격 = 5s
- 5s idle은 60s timeout 이내 → pool에 커넥션 생존 → 재사용

---

## 실험 실행

```bash
curl -s -X POST "http://localhost:8080/experiments/no-keep-alive?totalRequests=10&intervalMs=1000" | jq
curl -s -X POST "http://localhost:8080/experiments/short-idle?totalRequests=10&intervalMs=5000" | jq
curl -s -X POST "http://localhost:8080/experiments/long-idle?totalRequests=10&intervalMs=5000" | jq
```

---

## 실험 결과

### Scenario 1: no-keep-alive

```json
{
  "scenario": "NO_KEEP_ALIVE",
  "totalRequests": 10,
  "successCount": 0,
  "timeoutCount": 10,
  "timeoutRate": 1.0,
  "coldConnectionCount": 10,
  "avgLatencyMs": 162.3,
  "p50LatencyMs": 162,
  "p95LatencyMs": 172,
  "p99LatencyMs": 172
}
```

### Scenario 2: short-idle

```json
{
  "scenario": "SHORT_IDLE",
  "totalRequests": 10,
  "successCount": 0,
  "timeoutCount": 10,
  "timeoutRate": 1.0,
  "coldConnectionCount": 10,
  "avgLatencyMs": 161.8,
  "p50LatencyMs": 161,
  "p95LatencyMs": 170,
  "p99LatencyMs": 170
}
```

### Scenario 3: long-idle

```json
{
  "scenario": "LONG_IDLE",
  "totalRequests": 10,
  "successCount": 10,
  "timeoutCount": 0,
  "timeoutRate": 0.0,
  "coldConnectionCount": 0,
  "avgLatencyMs": 81.2,
  "p50LatencyMs": 81,
  "p95LatencyMs": 86,
  "p99LatencyMs": 86
}
```

---

## 결과 해석

### no-keep-alive: timeoutRate 1.0

`Connection: close` 헤더로 인해 응답 후 즉시 커넥션이 닫힌다. 모든 요청이 새 TCP 커넥션 → cold penalty(50ms) 부과 → ~160ms로 timeout(140ms) 초과. 예상과 정확히 일치.

### short-idle: timeoutRate 1.0

keep-alive 설정을 했음에도 no-keep-alive와 동일한 결과. `maxIdleTime=2s` < 요청 간격 5s이므로, Reactor Netty가 pool에서 커넥션을 제거한 뒤 다음 요청이 들어온다. 결과적으로 매번 새 커넥션 → cold 경로.

**핵심 인사이트**: keep-alive를 "켰다"는 것 자체가 의미 없다. idle timeout이 요청 간격보다 짧으면 keep-alive가 없는 것과 같다.

### long-idle: timeoutRate 0.0

`maxIdleTime=60s` > 요청 간격 5s. 커넥션이 pool에 살아있어 재사용된다. latency ~80ms로 timeout(140ms) 이내에 안정적으로 성공. cold penalty가 완전히 사라진 것을 확인.

---

## Phase 1 소결

client idle timeout 하나만으로 timeoutRate 0.0 ↔ 1.0을 오갈 수 있다. 이는 keep-alive 설정이 외부 API 연동의 안정성에 직접적인 영향을 미친다는 것을 수치로 증명한다.

단, Phase 1은 서버 설정이 기본값(60s)으로 충분히 길다는 전제 아래의 결과다. 서버 keep-alive timeout이 짧다면 long-idle도 실패할 수 있다 → Phase 2에서 검증.
