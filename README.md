# 📚 모빌리티 정산 시스템 - 원장 서비스 (Driver Ledger System)

이 프로젝트는 EasyADJ 모빌리티 정산 플랫폼의 핵심인 **원장(Ledger) 서비스**입니다.
결제(Payment) 서버와 정산(Settlement) 서버 사이에서 **이중기입 원장(Double-entry ledger)** 패턴을 통해 자금의 정합성을 보장하고, 이중 결제나 정산 불일치 등의 치명적인 금융 사고를 방지하는 역할을 수행합니다.

## 📌 과제 요구사항 요약
* **데이터 정합성 보장**: 플랫폼과 기사 간의 모든 거래를 차변(DEBIT)과 대변(CREDIT)으로 나누어 기록하고 합계가 정확히 일치하는지 검증합니다.
* **멱등성(Idempotency) 확보**: 타 서비스의 네트워크 재시도(Retry)로 인해 발생하는 중복 결제 기록을 방어합니다.
* **정산 대사(Reconciliation) 지원**: 정산 시스템이 정확한 기사 미지급금 내역을 가져갈 수 있도록 잔액 조회 및 대사 API를 제공합니다.

## 🚀 구현한 기능 목록
1. **분개 기록 API (`POST /api/ledger/entries`)**
    - Idempotency-Key 헤더를 통한 중복 요청 완벽 차단.
    - 차변과 대변의 합계 일치 여부를 트랜잭션 내에서 검증.
    - 정산 누락 방지를 위해 생성 완료 시 `ledger_id`를 반환.
2. **미지급 잔액 조회 API (`GET /api/ledger/unpaid`, `GET /api/ledger?driver_id=`)**
    - 부동소수점 오차 방지를 위해 모든 금액(amount) 데이터를 JSON `String` 형태로 제공.
    - 기사별 미지급금 총액 및 개별 결제 건 상세 내역 조회.
3. **JPA Auditing 기반 이력 관리**
    - 모든 분개의 생성 시간(`createdAt`)을 프레임워크 레벨에서 자동으로 기록하여 데이터 조작 및 누락 방지.

## ⚙️ 실행 방법
본 프로젝트는 보안 및 MSA 의존성 규칙에 따라 하드코딩을 배제하고 환경변수를 주입받아 실행됩니다.

1. **환경 변수 세팅** (IntelliJ 실행 구성 또는 `.env` 활용)
   ```properties
   SPRING_DATASOURCE_URL=jdbc:postgresql://{DB주소}
   SPRING_DATASOURCE_USERNAME={DB계정명}
   SPRING_DATASOURCE_PASSWORD={DB비밀번호}
   PAYMENT_API_BASE_URL=http://driver-payment-system... (결제 서버 베이스 URL)