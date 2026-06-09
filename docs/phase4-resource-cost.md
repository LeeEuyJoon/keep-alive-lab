# Phase 4 — resource-cost: keep-alive pool의 리소스 비용

## 핵심 질문

> keep-alive pool을 크게 유지하면 성능은 좋아지지만, 실제로 리소스를 얼마나 더 소모하는가?

Phase 3까지는 "warm이 좋다"는 것만 보였다. Phase 4는 그 반대편, 즉 **warm을 유지하는 비용**을 수치로 보여준다.

---

## 측정 지표

| 지표 | 의미 | 측정 방법 |
|---|---|---|
| `heapUsedMb` | 실험 후 JVM heap 사용량 | `Runtime.getRuntime()` |
| `openFdCount` | 실험 후 열린 파일 디스크립터 수 | `UnixOperatingSystemMXBean` |
| `successCount` / `errorCount` | pool 고갈 여부 | 기존 집계 |
| `avgLatencyMs` | 평균 응답 시간 | 기존 집계 |

FD(File Descriptor)는 TCP 커넥션 1개당 1개 소모된다. `openFdCount`가 높을수록 OS 레벨 리소스를 더 점유하고 있다는 의미.

---

## 실험 설계

### 기존 실험과의 차이

Phase 1~3은 **순차 요청** (1개씩 순서대로). Phase 4는 **동시 요청** (N개 동시에 발사).

순차 요청에서는 pool 크기와 무관하게 커넥션이 1개만 사용된다. pool 크기 차이를 드러내려면 동시 요청이 필수다.

```
concurrency=10, maxConnections=2:
  → 10개 요청 동시 발사
  → 2개만 즉시 커넥션 획득
  → 나머지 8개는 pool 대기 (200ms) → 획득 실패 → errorCount 증가

concurrency=10, maxConnections=10:
  → 10개 요청 동시 발사
  → 10개 모두 즉시 커넥션 획득 → 정상 처리
  → pool에 10개 커넥션 상시 점유 → FD 10개, heap 더 사용
```

---

## 사전 작업

### timeout 임계값 조정 (필수)

Phase 1~3의 `EXPERIMENT_TIMEOUT_MS=140ms`는 순차 요청 기준으로 설정된 값이다. 동시 cold 요청의 latency는 ~150ms로, 140ms timeout에 걸려 pool에 warm 커넥션이 쌓이지 않는다. Phase 4는 cold도 성공해야 pool이 채워지므로 200ms로 올린다.

```bash
EXPERIMENT_TIMEOUT_MS=200 docker compose up -d --build caller-service
```

실험 후 원복:
```bash
docker compose up -d --build caller-service
```

서버는 기본 상태(keep-alive 60s). external-api는 별도 설정 불필요.

---

## 시나리오

**고정값**: `concurrency = 10`, `repeat = 3`, `totalRequests = 30`

**변수**: `maxConnections`

| 시나리오 | maxConnections | 예상 동작 |
|---|---|---|
| A | 2 | pool 고갈 → errorCount 다수, FD 적음 |
| B | 5 | pool 부분 고갈 → errorCount 일부 |
| C | 10 | pool = concurrency → 성공, FD 중간 |
| D | 50 | pool 여유 → 성공, FD 높음 |

---

## 실험 실행

```bash
curl -s -X POST "http://localhost:8080/experiments/resource-cost?maxConnections=2&concurrency=10&repeat=3" | jq
curl -s -X POST "http://localhost:8080/experiments/resource-cost?maxConnections=5&concurrency=10&repeat=3" | jq
curl -s -X POST "http://localhost:8080/experiments/resource-cost?maxConnections=10&concurrency=10&repeat=3" | jq
curl -s -X POST "http://localhost:8080/experiments/resource-cost?maxConnections=50&concurrency=10&repeat=3" | jq
```

---

## 실험 환경

- `concurrency = 10`, `repeat = 3`, `totalRequests = 30`
- `EXPERIMENT_TIMEOUT_MS = 200ms`
- 서버 기본 상태 (keep-alive 60s)

**errorCount**: pool 슬롯을 얻지 못해 즉시 실패 (커넥션 획득 대기 200ms 초과)
**timeoutCount**: 커넥션은 얻었지만 응답이 200ms를 초과해 실패

---

## 실험 결과

### 시나리오 A: maxConnections=2

```json
{
  "scenario": "RESOURCE_COST_pool2_c10",
  "totalRequests": 30,
  "successCount": 6,
  "timeoutCount": 12,
  "errorCount": 12,
  "timeoutRate": 0.4,
  "avgLatencyMs": 113.93,
  "heapUsedMb": 25,
  "openFdCount": 36
}
```

### 시나리오 B: maxConnections=5

```json
{
  "scenario": "RESOURCE_COST_pool5_c10",
  "totalRequests": 30,
  "successCount": 18,
  "timeoutCount": 12,
  "errorCount": 0,
  "timeoutRate": 0.4,
  "avgLatencyMs": 168.83,
  "heapUsedMb": 30,
  "openFdCount": 40
}
```

### 시나리오 C: maxConnections=10

```json
{
  "scenario": "RESOURCE_COST_pool10_c10",
  "totalRequests": 30,
  "successCount": 30,
  "timeoutCount": 0,
  "errorCount": 0,
  "timeoutRate": 0,
  "avgLatencyMs": 113.33,
  "heapUsedMb": 32,
  "openFdCount": 50
}
```

### 시나리오 D: maxConnections=50

```json
{
  "scenario": "RESOURCE_COST_pool50_c10",
  "totalRequests": 30,
  "successCount": 30,
  "timeoutCount": 0,
  "errorCount": 0,
  "timeoutRate": 0,
  "avgLatencyMs": 120.83,
  "heapUsedMb": 33,
  "openFdCount": 60
}
```

---

## 비교 테이블

| maxConnections | successCount | errorCount | timeoutCount | openFdCount | heapUsedMb |
|---|---|---|---|---|---|
| 2 | 6 / 30 | 12 | 12 | 36 | 25 |
| 5 | 18 / 30 | 0 | 12 | 40 | 30 |
| 10 | 30 / 30 | 0 | 0 | 50 | 32 |
| 50 | 30 / 30 | 0 | 0 | 60 | 33 |

---

## 결과 해석

### pool=2: pool 고갈 → errorCount=12

concurrency=10인데 pool 슬롯이 2개뿐이다. 10개 동시 요청 중 2개만 즉시 커넥션 획득하고, 나머지 8개는 대기. `pendingAcquireTimeout(200ms)` 초과 → error. 3라운드 반복이므로 총 errorCount=12.

pool 고갈은 timeout이 아닌 **error**로 나타난다는 점이 중요하다. 서버가 느려서 timeout된 게 아니라, 커넥션 슬롯 자체를 얻지 못해 즉시 실패한 것이다.

### pool=5: 부분 고갈 → errorCount 사라지지만 timeoutCount 잔존

pool 슬롯이 5개라 errorCount는 0. 하지만 10개 동시 요청이 들어오면 5개는 즉시, 나머지 5개는 대기 후 순서대로 커넥션 획득. Round 1에서 10개 모두 cold 경로를 타게 되어 일부가 200ms 부근에서 timeout 발생.

### pool=10: successCount=30, 완벽

pool = concurrency. Round 1: 10개 모두 즉시 커넥션 획득, cold 경로(~150ms) → 200ms 이내 성공, pool에 10개 warm 커넥션 보관. Round 2, 3: warm 재사용(~100ms) → 안정적 성공.

### pool=50: pool=10과 성능 동일, 그러나 FD 더 점유

successCount, timeoutCount 모두 pool=10과 같다. 실제 사용하는 커넥션은 10개지만, ConnectionProvider가 maxConnections=50 기준으로 내부 리소스를 확보하므로 openFdCount=60 (pool=10의 50보다 10 더 높다). 성능상 이득은 없고 FD만 낭비한다.

---

## Phase 4 소결

| 구성 | 성능 | 리소스 | 결론 |
|---|---|---|---|
| pool < concurrency | 고갈 → error/timeout | 적음 | 요청 처리 실패 |
| pool = concurrency | 최적 | 적정 | 적정 설정 |
| pool > concurrency | 동일 | 낭비 | 불필요한 FD 점유 |

**keep-alive pool을 유지하는 리소스 비용**:
- 커넥션 1개당 FD 1개 상시 점유
- pool 크기 설정 자체가 FD를 소모 (실제 연결 여부와 무관)
- heap은 pool 크기에 비례해 소폭 증가 (커넥션 객체, 버퍼)

> **최적 maxConnections = 예상 최대 동시 요청 수**. 더 크게 잡을수록 FD와 heap을 낭비하고, 더 작게 잡으면 고갈로 요청이 실패한다.
