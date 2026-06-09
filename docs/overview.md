# Keep-Alive Lab — 실험 개요

## 실험 목적

HTTP Keep-Alive 설정이 외부 API 연동의 **latency와 timeout 발생률**에 어떤 영향을 주는지 수치로 확인한다.

"keep-alive를 잘 설정하면 좋다"는 말은 알고 있지만, 구체적으로 어떤 설정이 문제를 일으키고 어떤 설정이 안정적인지를 실험을 통해 직접 검증하는 것이 목적이다.

---

## 핵심 아이디어

외부 API를 호출할 때 latency는 두 경로로 나뉜다.

```
cold 경로 (새 TCP 커넥션):    TCP handshake + HTTP 처리
warm 경로 (기존 커넥션 재사용):             HTTP 처리
```

TCP handshake는 불필요한 오버헤드다. keep-alive는 이 오버헤드를 제거하기 위해 커넥션을 재사용한다. 실험은 이 차이를 **timeout 발생 여부**로 측정한다.

- warm 경로 → latency 낮음 → timeout 이내 → 성공
- cold 경로 → latency 높음 → timeout 초과 → 실패

---

## 실험 환경

### 구성

```
caller-service (8080)  →  toxiproxy (8888)  →  external-api (8081)
     [client]               [TCP proxy]            [server]
```

- **caller-service**: WebClient(Reactor Netty)로 external-api를 호출하는 클라이언트. 시나리오별로 keep-alive 설정을 다르게 적용.
- **toxiproxy**: 양방향 15ms latency를 추가하는 TCP 레벨 프록시. cold/warm 경로 간 latency 차이를 벌리고, RST 패킷을 그대로 전달하는 역할.
- **external-api**: 요청을 받아 50ms 처리 후 응답. 새 커넥션(새 remotePort)이면 추가 50ms cold penalty 부과.

### 핵심 수치

```
external-api 처리 시간:      50ms
cold penalty (새 커넥션):    50ms
Toxiproxy 양방향 latency:   30ms (15ms × 2)
EXPERIMENT_TIMEOUT_MS:      140ms  (Phase 1~3)  /  200ms  (Phase 4)

cold 경로 예상 latency:  50(penalty) + 50(work) + 30(toxiproxy) = ~160ms → timeout 초과
warm 경로 예상 latency:              50(work) + 30(toxiproxy) = ~80ms  → timeout 이내
```

timeout 임계값 140ms는 cold(~160ms)는 실패하고 warm(~80ms)은 성공하도록 의도적으로 설정한 경계값이다.

### Toxiproxy를 쓰는 이유

1. **경계선 확보**: toxiproxy 없이는 cold/warm 차이가 좁아 timeout 임계값 설정이 불안정하다. 양쪽에 30ms를 더해 차이를 충분히 벌린다.
2. **RST 전달**: TCP 레벨 프록시라서 서버가 보내는 RST 패킷을 HTTP 해석 없이 그대로 통과시킨다. Phase 2 시나리오에서 필수.

---

## Phase 구성

| Phase | 핵심 질문 | 시나리오 |
|---|---|---|
| Phase 1 | client 설정만으로 차이가 나는가? | no-keep-alive, short-idle, long-idle |
| Phase 2 | 서버가 먼저 끊으면 어떻게 되나? | server-short |
| Phase 3 | 서버 설정 변경 불가 시 해결책은? | warm-up-off, warm-up-on |
| Phase 4 | warm pool 유지의 리소스 비용은? | resource-cost (pool 크기별) |

---

## 전체 결론

> **커넥션이 warm 상태를 유지하려면, 요청 간격이 client idle timeout과 server keep-alive timeout 둘 다보다 짧아야 한다.**

```
조건: 요청 간격 < min(client idle timeout, server keep-alive timeout)
```

| 조건 위반 | 실패한 시나리오 |
|---|---|
| keep-alive 자체 없음 | no-keep-alive |
| 요청 간격 > client idle timeout | short-idle |
| 요청 간격 > server keep-alive timeout | server-short, warm-up-off |

### keep-alive가 실제로 하는 일

keep-alive가 latency를 줄이는 게 아니다. **TCP 재연결 오버헤드를 제거**하는 것이다.

```
cold 경로 = TCP handshake(오버헤드) + HTTP 처리(고정)
warm 경로 =                           HTTP 처리(고정)
```

### 실용적 시사점

1. **외부 API 연동 시 서버 설정을 확인해야 한다.** client만 잘 설정해도 상대방 서버 keep-alive timeout이 짧으면 의미 없다.
2. **트래픽이 뜸한 구간의 timeout은 keep-alive 문제일 수 있다.** 새벽이나 배포 직후 갑자기 timeout이 터지는 패턴의 원인.
3. **서버 설정 변경이 불가능하면 ping으로 해결 가능하다.** 단, ping도 리소스 비용이 있으므로 최솟값으로 설정해야 한다.

### ping의 trade-off

| 리소스 | 내용 |
|---|---|
| 파일 디스크립터 | 커넥션 1개 = OS FD 1개 상시 점유 |
| 메모리 | connection pool이 커넥션 상태·버퍼를 heap에 보관 |
| 스케줄러 | ping 타이머가 상시 실행 |
| 네트워크 | ping마다 TCP 패킷 송수신 |

ping 간격은 "가능한 짧게"가 아니라 **"server keep-alive timeout보다 조금 짧은 최솟값"** 이 최적이다.
