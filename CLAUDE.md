# CLAUDE.md

## 클로드 자율 운영 규칙

이 파일과 메모리 시스템은 클로드가 자율적으로 유지 관리한다.
별도 요청 없이도 아래 규칙을 항상 실행할 것.

### CLAUDE.md 업데이트
- 새로운 기술 스택, 디렉토리 구조, 의사결정 등 어떠한 형태로든 파악된 추가적인 정보가 이후 작업에서 참고할 여지가 있다고 판단될 때마다 적극적으로 해당 섹션을 업데이트한다.

### 메모리 구조
모든 지식 파일(.md)은 `.claude/memory/` 하나로 통합한다. 기획서, 설계 문서, 클로드가 생성한 메모리 파일 모두 이 안에 둔다.
MEMORY.md 인덱스만 두 곳에 동기화한다.
- 인덱스 원본: `~/.claude/projects/-Users-luti-Documents-project-mini-keep-alive/memory/MEMORY.md`
- 인덱스 사본: `.claude/memory/MEMORY.md`

MEMORY.md를 수정할 때마다 두 경로를 동시에 업데이트한다. 동기화 누락 없음.
모든 파일 경로는 두 MEMORY.md 모두 프로젝트 절대 경로로 작성한다. URL 인코딩(%20 등) 없이 공백은 그대로 사용한다.

### MEMORY.md 인덱스 형식
인덱스는 카테고리별로 구성한다. 카테고리와 파일은 파악된 내용에 따라 자유롭게 생성하고, 필요하면 언제든 재구성한다.
`.claude/memory/` 내부의 디렉토리 구조도 파일이 늘어나면 자유롭게 재구성한다.

인덱스 형식:

    # Memory Index

    ## <카테고리명>
    - [파일 제목](/절대경로/.claude/memory/파일명.md) — 한 줄 요약

---

## 프로젝트 정보

- **프로젝트명**: Keep-Alive Lab
- **목적**: HTTP Keep-Alive 설정이 외부 API 연동의 latency와 timeout 발생률에 어떤 영향을 주는지 실험
- **구조**: Docker Compose로 3개 서비스 실행
  - `caller-service` (포트 8080): WebClient로 external-api를 호출하는 클라이언트
  - `toxiproxy` (포트 8474 API, 8888 proxy): 양방향 15ms latency 추가
  - `external-api` (포트 8081): 50ms 처리 지연. 인위적 패널티 없음.
- **기술 스택**: Java 17, Spring Boot 3.x, Gradle, Spring WebFlux WebClient, Spring Web MVC, Toxiproxy, Docker, Docker Compose
- **DB**: 사용하지 않음. 실험 결과는 메모리 집계 후 JSON 반환

## 핵심 실험 파라미터 (v2)

```
Toxiproxy 양방향 latency:   15ms
external-api 처리 시간:      50ms
caller-service timeout:     100ms

새 커넥션:    TCP handshake(45ms) + HTTP(80ms) = 125ms → timeout 초과
재사용 커넥션: HTTP(80ms) = 80ms → 성공
```

인위적 cold/warm 패널티 없음. 실제 TCP handshake 비용으로 timeout 차이를 측정한다.

## 실험 시나리오 목록

| 시나리오 | 엔드포인트 | 핵심 설정 |
|---|---|---|
| No Keep-Alive | POST /experiments/no-keep-alive | Connection: close |
| Short Idle | POST /experiments/short-idle | client maxIdleTime: 2s, 요청간격: 5s |
| Long Idle | POST /experiments/long-idle | client maxIdleTime: 60s, 요청간격: 5s |
| Server Short | POST /experiments/server-short | server keep-alive: 2s, 요청간격: 5s (재시작 필요) |
| Warm-up Off | POST /experiments/warm-up-off | 요청간격: 30s, server keep-alive: 20s |
| Warm-up On | POST /experiments/warm-up-on | warm-up /ping 10초 간격 |

## 디렉토리 구조

```
keep-alive/
├── CLAUDE.md
├── docker-compose.yml          (예정)
├── caller-service/             (예정)
│   ├── Dockerfile
│   ├── build.gradle
│   └── src/...
├── external-api/               (예정)
│   ├── Dockerfile
│   ├── build.gradle
│   └── src/...
├── README.md                   (예정)
└── .claude/
    └── memory/
        ├── MEMORY.md
        ├── planning/
        │   ├── 초기 기획.md   (v1, 폐기)
        │   └── 기획 v2.md    (현행)
        └── dev/
            └── 개발 가이드.md
```
