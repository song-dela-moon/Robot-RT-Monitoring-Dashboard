# 🤖 Robot Monitor Dashboard (로봇 모니터링 실시간 대시보드)

이 프로젝트는 다수의 로봇으로부터 발생하는 실시간 로그 데이터를 처리하고 실시간으로 시각화하는 모니터링 시스템입니다. 특히 **Spring WebFlux**를 활용한 **Back Pressure** 제어와 **Server-Sent Events (SSE)**를 통한 데이터 스트리밍에 초점을 맞추고 있습니다.

## 🚀 주요 기능

### 1. 실시간 모니터링 (High-Resolution)
*   **1초 주기 업데이트**: 매초 발생하는 로봇의 상태(CPU, 메모리, 위치, 속도)를 실시간으로 대시보드에 반영합니다.
*   **60초 슬라이딩 윈도우**: 최근 1분간의 데이터를 유지하며 차트가 실시간으로 흐르는 효과를 제공합니다.
*   **SSE 스트리밍**: WebSocket보다 가벼운 SSE 방식을 채택하여 서버 부하를 최소화하면서 실시간성을 확보했습니다.

### 2. 효율적인 Back Pressure 제어
*   **Zone 2 (Generator → DB)**: `onBackpressureBuffer(512, DROP)` 전략을 사용해 DB 저장 속도보다 생성 속도가 빠를 경우 오래된 로그를 버려 시스템 안정성을 유지합니다.
*   **Zone 1 (DB → SSE)**: `onBackpressureBuffer(256, DROP_OLDEST)` 전략을 통해 클라이언트의 수신 속도가 느릴 경우 최신 데이터를 우선적으로 전달합니다.

### 3. 과거 데이터 조회
*   **일간/주간/월간 조회**: R2DBC를 통한 비동기 DB 조회를 지원하며, 방대한 양의 과거 데이터를 차트로 시각화합니다.
*   **타임존 보정**: 브라우저의 로컬 타임존을 기반으로 한 정확한 시간대별 데이터 조회를 보장합니다.

## 🛠️ 기술 스택

### Backend
*   **Framework**: Spring Boot 3.4.1 (WebFlux)
*   **Database**: MySQL 8.0 (R2DBC)
*   **Streaming**: Server-Sent Events (SSE)
*   **Build Tool**: Gradle

### Frontend
*   **Framework**: Next.js 15 (App Router)
*   **UI/Chart**: Tailwind CSS, Recharts
*   **Visuals**: Modern Glassmorphism & Dark Mode Aesthetic

## 📖 설치 및 실행 방법

자세한 설치 단계와 데이터베이스 설정은 [SETUP.md](./SETUP.md) 파일을 참조해 주세요.

## 📁 프로젝트 구조

프로젝트 세부 구조는 [SETUP.md#4-디렉토리-구조](./SETUP.md#4-디렉토리-구조) 섹션에 상세히 기술되어 있습니다.
