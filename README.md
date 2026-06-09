# Keep-Alive Lab

HTTP Keep-Alive 설정이 외부 API 연동의 latency와 timeout 발생률에 어떤 영향을 주는지 수치로 검증하는 실험 프로젝트.

---

## 실험 배경

외부 API를 호출할 때 레이턴시는 두 경로로 나뉜다.

```
cold 경로 (새 TCP 커넥션):    TCP handshake + HTTP 처리  → 느림
warm 경로 (기존 커넥션 재사용):             HTTP 처리  → 빠름
```

TCP handshake는 순수한 오버헤드다. keep-alive는 이 오버헤드를 제거하기 위해 커넥션을 재사용한다. 이 실험은 그 차이를 **timeout 발생 여부**로 측정한다.

---

## 실험 환경

```
caller-service (8080)  →  toxiproxy (8888)  →  external-api (8081)
     [client]               [TCP proxy]            [server]
```

| 서비스 | 역할 |
|---|---|
| caller-service | WebClient(Reactor Netty)로 external-api 호출. 시나리오별 keep-alive 설정 적용 |
| toxiproxy | 양방향 15ms latency 추가. cold/warm 경로 간 latency 차이를 벌리고, RST 패킷을 TCP 레벨에서 그대로 전달 |
| external-api | 요청 수신 후 50ms 처리. 새 커넥션(새 remotePort)이면 cold penalty 50ms 추가 |

### 핵심 수치

```
external-api 처리:       50ms
cold penalty:            50ms  (새 커넥션에만 부과)
toxiproxy 양방향 latency: 30ms  (15ms x 2)
timeout 임계값:          140ms  (Phase 1~3) / 200ms (Phase 4)

cold 경로 예상: 50 + 50 + 30 = ~160ms  → timeout 초과
warm 경로 예상:      50 + 30 = ~80ms   → timeout 이내
```

timeout 임계값은 cold는 실패하고 warm은 성공하도록 그 사이에 설정한 경계값이다.

---

## Phase 구성

| Phase | 핵심 질문 | 시나리오 |
|---|---|---|
| [Phase 1](docs/phase1-basic.md) | client 설정만으로 차이가 나는가? | no-keep-alive, short-idle, long-idle |
| [Phase 2](docs/phase2-server-short.md) | 서버가 먼저 끊으면 어떻게 되나? | server-short |
| [Phase 3](docs/phase3-warmup.md) | 서버 설정 변경 불가 시 해결책은? | warm-up-off, warm-up-on |
| [Phase 4](docs/phase4-resource-cost.md) | warm pool 유지의 리소스 비용은? | resource-cost (pool 크기별) |

전체 실험 환경 및 결론 요약 → [docs/overview.md](docs/overview.md)

---

## 전체 결론

> **커넥션이 warm 상태를 유지하려면, 요청 간격이 client idle timeout과 server keep-alive timeout 둘 다보다 짧아야 한다.**

```
조건: 요청 간격 < min(client idle timeout, server keep-alive timeout)
```

| 조건 위반 | 실패한 시나리오 | timeoutRate |
|---|---|---|
| keep-alive 자체 없음 | no-keep-alive | 1.0 |
| 요청 간격 > client idle timeout | short-idle | 1.0 |
| 요청 간격 > server keep-alive timeout | server-short, warm-up-off | 0.9, 0.8 |
| 두 조건 모두 충족 | long-idle, warm-up-on | 0.0 |

keep-alive가 latency를 줄이는 게 아니다. **TCP 재연결 오버헤드를 제거**하는 것이다. cold 경로와 warm 경로의 latency 차이는 그 오버헤드의 크기다.

---

## 기술 스택

- Java 17, Spring Boot 3.x, Gradle
- Spring WebFlux WebClient (Reactor Netty)
- Spring Web MVC
- Toxiproxy
- Docker, Docker Compose

---

## 실행 방법

```bash
# 전체 서비스 시작
docker compose up -d --build

# Phase 1 예시
curl -s -X POST "http://localhost:8080/experiments/no-keep-alive?totalRequests=10&intervalMs=1000" | jq
curl -s -X POST "http://localhost:8080/experiments/short-idle?totalRequests=10&intervalMs=5000" | jq
curl -s -X POST "http://localhost:8080/experiments/long-idle?totalRequests=10&intervalMs=5000" | jq
```

각 Phase별 실행 방법과 사전 작업은 해당 문서를 참조.
