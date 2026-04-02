# Robot RT Monitoring Dashboard - 전체 구조 및 기능 설명

## 프로젝트 개요

**백프레셔(Back-Pressure)** 를 학습하기 위한 실시간 로봇 모니터링 대시보드 백엔드입니다.  
Spring WebFlux + Reactor + R2DBC 기반의 완전 비동기·논블로킹 구조로 구성되어 있습니다.

- **포트**: 8080
- **DB**: MySQL (`robot_monitor`)
- **주요 기술**: Spring WebFlux, Project Reactor, R2DBC, SSE

---

## 디렉토리 구조

```
back-pressure-practice/
├── build.gradle                    # Gradle 빌드 설정
├── settings.gradle                 # 프로젝트 이름 설정
└── src/main/
    ├── java/com/example/
    │   ├── WebfluxReactiveStreamsTestApplication.java   # 앱 진입점
    │   ├── config/
    │   │   └── CorsConfig.java                         # CORS 설정
    │   ├── controller/
    │   │   └── RobotController.java                    # REST + SSE 엔드포인트
    │   ├── dto/
    │   │   └── RobotLogEvent.java                      # SSE 전송용 DTO (record)
    │   ├── entity/
    │   │   └── RobotLog.java                           # DB 엔티티
    │   ├── repository/
    │   │   └── RobotLogRepository.java                 # Reactive R2DBC 레포지토리
    │   └── service/
    │       ├── RobotDummyDataService.java              # 더미 데이터 생성 + 실시간 시뮬레이터
    │       └── RobotSseService.java                    # SSE 스트림 관리
    └── resources/
        ├── application.yaml                            # 설정 파일
        ├── schema.sql                                  # 테이블 정의
        └── data.sql                                    # 초기 데이터 (비어있음)
```

---

## 전체 아키텍처 구조도

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          CLIENT (Frontend Dashboard)                         │
│              localhost:3000 (Next.js) / localhost:5173 (Vite)               │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ HTTP / SSE
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         RobotController (Port 8080)                          │
│                                                                               │
│  GET /api/robots                     → 로봇 ID 목록 반환                      │
│  GET /api/robots/{id}/logs?from=&to= → 기간별 이력 조회 (JSON Flux)          │
│  GET /api/robots/{id}/stream         → SSE 실시간 스트림 구독                 │
└──────────┬────────────────────────────────────┬──────────────────────────────┘
           │                                    │
           ▼                                    ▼
┌──────────────────────┐            ┌───────────────────────────────────────┐
│  RobotLogRepository  │            │           RobotSseService              │
│  (R2DBC Reactive)    │            │                                        │
│                      │            │  Map<robotId, Sinks.Many>              │
│  - 기간 조회          │            │  ┌─────────────────────────────────┐  │
│  - 로봇 ID 목록       │            │  │ Sink (robot-001)                │  │
│  - 이력 데이터 유무   │            │  │ onBackpressureBuffer(256)       │  │
│    확인              │            │  │ → DROP_OLDEST (Zone 1)          │  │
└──────────┬───────────┘            │  └─────────────────────────────────┘  │
           │                        │  ┌─────────────────────────────────┐  │
           │                        │  │ Sink (robot-002) ... (robot-005)│  │
           │                        │  └─────────────────────────────────┘  │
           │                        └───────────────────┬───────────────────┘
           │                                            │ pushEvent()
           │                        ┌───────────────────┴───────────────────┐
           └───────────────────────►│        RobotDummyDataService           │
                                    │                                        │
                                    │  [Phase 1] @PostConstruct              │
                                    │  → 30일치 이력 데이터 Bulk Insert       │
                                    │    (concurrency=8, 로봇당 ~43,200건)   │
                                    │                                        │
                                    │  [Phase 2] 실시간 생성 루프             │
                                    │  Flux.interval(1s)                     │
                                    │  → onBackpressureBuffer(512, DROP)     │
                                    │    (Zone 2)                            │
                                    │  → flatMap(save, concurrency=4)        │
                                    │  → sseService.pushEvent()              │
                                    └───────────────────────────────────────┘
                                                       │
                                                       ▼
                                    ┌───────────────────────────────────────┐
                                    │            MySQL Database              │
                                    │         robot_monitor.robot_logs       │
                                    │    (R2DBC Pool: min=5, max=20)         │
                                    └───────────────────────────────────────┘
```

---

## 데이터 흐름도 (Data Flow)

```
앱 시작
  │
  ├─[Phase 1: 이력 데이터 삽입]──────────────────────────────────────────────┐
  │   robot-001 ~ robot-005 각 로봇                                          │
  │   └─ DB에 cutoff(1일 전) 이전 데이터가 없으면 30일치 생성                  │
  │      Flux.range(0, 43200)                                                │
  │        .map(i → buildRobotLog(robotId, now - 30days + i분))              │
  │        .flatMap(save(), concurrency=8)  ──→  MySQL INSERT                │
  │        (5,000건마다 진행률 로그 출력)                                      │
  └──────────────────────────────────────────────────────────────────────────┘
  │
  └─[Phase 2: 실시간 생성 루프 시작]
       │
       Flux.interval(1초)
         │
         ├─ buildRobotLog("robot-001") ─┐
         ├─ buildRobotLog("robot-002") ─┤
         ├─ buildRobotLog("robot-003") ─┼─ Flux<RobotLog> (5건/초)
         ├─ buildRobotLog("robot-004") ─┤
         └─ buildRobotLog("robot-005") ─┘
                    │
                    ▼
       [Zone 2 Back-Pressure]
       .onBackpressureBuffer(512, DROP)
       .flatMap(log → repository.save(log), concurrency=4)
                    │
                    ▼ 저장 성공
       RobotLogEvent.from(savedLog)
                    │
                    ▼
       sseService.pushEvent(event)
                    │
                    ▼
       해당 robotId의 Sink에 emit
                    │
                    ▼
       [Zone 1 Back-Pressure]
       .onBackpressureBuffer(256, DROP_OLDEST)
                    │
                    ▼
       구독 중인 SSE 클라이언트로 전송
```

---

## 백프레셔(Back-Pressure) 2개 구간 상세

### Zone 1 - SSE 느린 클라이언트 처리 (`RobotSseService`)

```
[데이터 생산자: RobotDummyDataService]
          │  pushEvent() 호출 (1초당 1건/로봇)
          ▼
    Sinks.many().multicast()
          │
          ▼
    .onBackpressureBuffer(256, DROP_OLDEST)
    │
    ├─ 버퍼 여유 있음 → 정상 전송
    └─ 버퍼(256) 가득 참 → 가장 오래된 이벤트 DROP
          │
          ▼
    SSE 클라이언트 (느린 연결 or 네트워크 지연)
```

**전략**: DROP_OLDEST - 최신 데이터를 유지하고 오래된 데이터를 버림  
**적합한 이유**: 실시간 모니터링에서는 최신 상태가 더 중요함

---

### Zone 2 - DB 쓰기 병목 처리 (`RobotDummyDataService`)

```
[Flux.interval(1초) - 생산 속도 고정]
          │
          ▼
    buildRobotLog() × 5 (로봇 수)
          │
          ▼
    .onBackpressureBuffer(512, DROP)
    │
    ├─ 버퍼 여유 있음 → DB 저장 진행
    └─ 버퍼(512) 가득 참 → 새 이벤트 DROP
          │
          ▼
    .flatMap(save(), concurrency=4)
    (동시 DB 커넥션 최대 4개로 제한)
          │
          ▼
    MySQL INSERT
```

**전략**: DROP (새 이벤트 버림) - DB가 느릴 때 큐 초과분 폐기  
**적합한 이유**: 더미 데이터이므로 일부 손실 허용 가능

---

## 클래스별 상세 기능

### 1. `WebfluxReactiveStreamsTestApplication.java`
- Spring Boot 앱 진입점
- `@SpringBootApplication` 자동 설정 활성화
- 포트 8080으로 실행

---

### 2. `CorsConfig.java`
- 프론트엔드 개발 서버에서의 API 호출 허용
- 허용 Origin: `http://localhost:3000`, `http://localhost:5173`
- 모든 HTTP 메서드, 헤더, 자격증명 허용

---

### 3. `RobotController.java` - REST + SSE 엔드포인트

| 엔드포인트 | 메서드 | 설명 |
|-----------|--------|------|
| `/api/robots` | GET | DB에서 로봇 ID 목록 반환 |
| `/api/robots/{robotId}/logs` | GET | `from`, `to` 파라미터로 기간 조회 |
| `/api/robots/{robotId}/stream` | GET | SSE 실시간 스트림 (minPriority 기본값=3) |

**SSE 응답 형식**:
```
event: robot-log
id: 1042
data: {"id":1042,"robotId":"robot-001","cpuUsage":52.3,...}
```

---

### 4. `RobotLog.java` - DB 엔티티

| 필드 그룹 | 필드명 | 설명 |
|----------|--------|------|
| 로봇 식별 | `robotId` | "robot-001" ~ "robot-005" |
| 시스템 지표 | `cpuUsage`, `memUsed`, `memTotal` | CPU %, 메모리 (vmstat 기반) |
| 프로세스 | `procsRunning`, `procsBlocked` | 실행 중/블록된 프로세스 수 |
| 우선순위 | `priority` | 1(위험) ~ 5(정상) |
| 위치 | `posX`, `posY`, `posZ` | ROS 오도메트리 위치 (미터) |
| 속도 | `velLinearX`, `velLinearY`, `velAngularZ` | 선속도/각속도 |
| ROS 메타 | `rosFrameId`, `rosTopic` | "odom", "/robot001/odom" |
| 타임스탬프 | `recordedAt` | 기록 시각 |

---

### 5. `RobotLogEvent.java` - SSE 전송용 DTO

- Java `record`로 불변(immutable) 객체
- `RobotLog` 엔티티와 동일 필드 구성
- 정적 팩토리 메서드 `from(RobotLog)` 제공
- SSE 전송 전용 (DB와 분리)

---

### 6. `RobotLogRepository.java` - Reactive Repository

```java
// 기간 조회
Flux<RobotLog> findByRobotIdAndRecordedAtBetween(String robotId, LocalDateTime from, LocalDateTime to)

// 이력 데이터 존재 여부 확인
Mono<Long> countHistoricalByRobotId(String robotId, LocalDateTime cutoff)

// 로봇 ID 목록
Flux<String> findDistinctRobotIds()
```

- R2DBC 기반으로 완전 비동기 DB 접근
- 커스텀 복합 인덱스 `(robot_id, recorded_at)` 활용

---

### 7. `RobotSseService.java` - SSE 스트림 관리 (Zone 1)

```
역할: 로봇별 Hot Publisher 관리

내부 구조:
Map<String, Sinks.Many<RobotLogEvent>>
  ├── "robot-001" → Sink → Flux (멀티캐스트)
  ├── "robot-002" → Sink → Flux
  └── ...

주요 메서드:
  streamFor(robotId) → Flux<RobotLogEvent>  // 클라이언트 구독
  pushEvent(event)                           // 이벤트 발행

드롭 카운터: AtomicLong sseDropCount (모니터링용)
```

**Hot Publisher**: 구독 전 발행된 이벤트는 받을 수 없음 (TV 방송과 동일)

---

### 8. `RobotDummyDataService.java` - 데이터 생성 서비스 (Zone 2)

**더미 데이터 생성 알고리즘 (`buildRobotLog`)**:

| 항목 | 생성 방식 |
|------|-----------|
| CPU 사용률 | 가우시안 분포 (평균 45%, 표준편차 20%) |
| 메모리 | 전체 16GB 중 30~80% 랜덤 |
| 프로세스 | 가우시안(실행중), 랜덤(블록됨) |
| 우선순위 | CPU 기반: ≥85%→1, 65~85%→2, 40~65%→3, 20~40%→4, <20%→5 |
| 위치 | 사인파 기반 원형 이동 + 가우시안 노이즈 |
| 속도 | 위치 변화 기반 계산 |

---

## DB 스키마

```sql
CREATE TABLE robot_logs (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    robot_id      VARCHAR(50)    NOT NULL,
    cpu_usage     DOUBLE         NOT NULL,
    mem_used      BIGINT         NOT NULL,
    mem_total     BIGINT         NOT NULL,
    procs_running INT            NOT NULL,
    procs_blocked INT            NOT NULL,
    priority      INT            NOT NULL,
    pos_x         DOUBLE,
    pos_y         DOUBLE,
    pos_z         DOUBLE,
    vel_linear_x  DOUBLE,
    vel_linear_y  DOUBLE,
    vel_angular_z DOUBLE,
    ros_frame_id  VARCHAR(50),
    ros_topic     VARCHAR(100),
    recorded_at   DATETIME(3)    NOT NULL,

    INDEX idx_robot_time (robot_id, recorded_at)
);
```

---

## 우선순위(Priority) 기준

```
CPU ≥ 85%  → Priority 1 (Critical - 위험)
CPU 65~85% → Priority 2 (High - 높음)
CPU 40~65% → Priority 3 (Normal - 보통)
CPU 20~40% → Priority 4 (Low - 낮음)
CPU < 20%  → Priority 5 (Idle - 유휴)
```

SSE 구독 시 `minPriority=3`이면 Priority 1, 2, 3 이벤트만 수신 (높은 중요도만 필터링)

---

## API 사용 예시

### 로봇 목록 조회
```
GET /api/robots
→ ["robot-001", "robot-002", "robot-003", "robot-004", "robot-005"]
```

### 기간별 이력 조회
```
GET /api/robots/robot-001/logs?from=2026-03-25T00:00:00&to=2026-04-01T23:59:59
→ Flux<RobotLog> (JSON 배열 스트림)
```

### 실시간 SSE 구독 (Priority 1~2만)
```
GET /api/robots/robot-001/stream?minPriority=2
→ text/event-stream (지속 연결)

event: robot-log
id: 2041
data: {"robotId":"robot-001","cpuUsage":87.3,"priority":1,...}
```

---

## 핵심 기술 요약

| 개념 | 구현체 |
|------|--------|
| 리액티브 프로그래밍 | Project Reactor (`Flux`, `Mono`, `Sinks`) |
| 백프레셔 | `onBackpressureBuffer` + DROP 전략 (2개 구간) |
| 실시간 스트리밍 | Server-Sent Events + Hot Publisher |
| 논블로킹 DB | R2DBC MySQL + Spring Data R2DBC |
| 동시성 제어 | `flatMap(concurrency=N)` + 커넥션 풀 |
| 데이터 시뮬레이션 | 가우시안 분포 + 사인파 기반 더미 생성 |
| CORS | 프론트엔드 개발 서버 허용 설정 |
