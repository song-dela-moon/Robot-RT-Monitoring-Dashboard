# Robot Monitor — Setup & Run Guide

## 1. MySQL 데이터베이스 생성

```sql
-- MySQL 접속 후 실행
CREATE DATABASE robot_monitor CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER 'robot_user'@'localhost' IDENTIFIED BY 'robot_pass';
GRANT ALL PRIVILEGES ON robot_monitor.* TO 'robot_user'@'localhost';
FLUSH PRIVILEGES;
```

> **테이블은 자동으로 생성됩니다.**  
> `application.yaml`의 `spring.sql.init.schema-locations: classpath:schema.sql` 설정으로  
> 앱 시작 시 `robot_logs` 테이블을 자동으로 생성합니다.

---

## 2. 백엔드 실행 (Spring Boot — port 8080)

```bash
# 프로젝트 루트로 이동 (back-pressure-practice)
cd back-pressure-practice

# 의존성 다운로드 + 빌드 + 실행
./gradlew bootRun
```

> **최초 실행 시**: `@PostConstruct`에서 로봇 5대의 30일치 과거 데이터를 자동으로 삽입합니다.  
> (robot-001 ~ 005, 1분 간격 → 약 216,000건)  
> 삽입이 완료된 후 실시간 데이터 생성(1초 간격)이 시작됩니다.

### API 확인
```bash
# 로봇 목록
curl http://localhost:8080/api/robots

# 과거 로그 (daily 예시 - KST 기준)
curl "http://localhost:8080/api/robots/robot-001/logs?from=2026-04-01T00:00:00&to=2026-04-01T23:59:59"

# SSE 실시간 수신
curl -N "http://localhost:8080/api/robots/robot-001/stream"
```

---

## 3. 프론트엔드 실행 (Next.js — port 3000)

```bash
# 프로젝트 루트로 이동 (robot-dashboard)
cd robot-dashboard

# 최초 1회: 의존성 설치
npm install

# 개발 서버 시작
npm run dev
```

브라우저에서 `http://localhost:3000` 접속

---

## 4. 디렉토리 구조

```
BackPressurePractice/
├── .gitignore
├── README.md                          ← 프로젝트 개요
├── SETUP.md                            ← 이 파일
│
├── back-pressure-practice/             ← Spring Boot (Backend)
│   ├── build.gradle
│   ├── src/main/
│   │   ├── java/com/example/
│   │   │   ├── controller/
│   │   │   │   └── RobotController.java          ← REST + SSE 엔드포인트
│   │   │   ├── entity/
│   │   │   │   └── RobotLog.java                 ← R2DBC 엔티티 (logs)
│   │   │   ├── repository/
│   │   │   │   └── RobotLogRepository.java
│   │   │   ├── service/
│   │   │   │   ├── RobotSseService.java          ← SSE 스트림 관리
│   │   │   │   └── RobotDummyDataService.java    ← 더미 생성기
│   │   │   ├── dto/
│   │   │   │   └── RobotLogEvent.java            ← SSE 전송용 DTO
│   │   │   └── config/
│   │   │       └── CorsConfig.java               ← CORS (3000 포트) 허용
│   │   └── resources/
│   │       ├── application.yaml                  ← R2DBC & DB 설정
│   │       └── schema.sql                        ← 로봇 로그 테이블 DDL
│
└── robot-dashboard/                    ← Next.js (Frontend)
    ├── package.json
    ├── app/
    │   ├── layout.tsx
    │   ├── page.tsx                    ← 메인 대시보드 (전체 통합)
    │   └── globals.css
    ├── components/
    │   ├── RobotSelector.tsx           ← 로봇 필터링
    │   ├── PeriodSelector.tsx          ← 조회 기간 선택 (초간/일간 등)
    │   ├── SseConnector.tsx            ← 실시간 SSE 수신 모듈
    │   ├── LogChart.tsx                ← 실시간 모니터링 차트 (Recharts)
    │   └── StatsPanel.tsx              ← 핵심 지표 요약 패널
    └── types/index.ts
```

---

## 5. Back Pressure 구조 요약

```
[더미 생성 1s]
     │
     │ Zone 2: onBackpressureBuffer(512, DROP)
     │         flatMap(concurrency=4)
     ▼
[MySQL: robot_logs]
     │
     │ DB 저장 완료 → pushEvent()
     │ Zone 1: Sinks.many().onBackpressureBuffer(256, DROP_OLDEST)
     ▼
[SSE /api/robots/{id}/stream]
     │
     ▼
[Next.js EventSource → 차트 실시간 업데이트]
```
