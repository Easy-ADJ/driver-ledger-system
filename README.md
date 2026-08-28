# 📚 Driver Ledger System

EasyADJ 모빌리티 정산 플랫폼의 **원장(Ledger) 서비스**입니다.

결제(Payment) 서비스에서 발생한 결제 데이터를 조회하여 **이중기입 원장(Double-entry Ledger)** 방식으로 기록하고, 정산(Settlement) 서비스가 기사별 미지급금과 결제 근거를 조회할 수 있도록 제공합니다.

원장 서비스는 결제와 정산 사이에서 금액의 정합성을 유지하고, 결제 발생부터 취소·정산 지급까지의 변경 이력을 원장 기록으로 추적할 수 있도록 하는 것을 목표로 합니다.

---

# 🏗 System Architecture

EasyADJ는 Payment, Ledger, Settlement 서비스를 각각 독립된 서버와 DB로 구성합니다.

```text
┌──────────────────┐
│  Payment Server  │
│    Payment DB    │
└────────┬─────────┘
         │
         │ HTTP API
         │ GET /api/ledger?driver_id=
         ▼
┌──────────────────┐
│  Ledger Server   │
│    Ledger DB     │
└────────┬─────────┘
         │
         │ HTTP API
         ▼
┌──────────────────┐
│Settlement Server │
│  Settlement DB   │
└────────┬─────────┘
         │
         │ POST /api/ledger/entries
         ▼
┌──────────────────┐
│  Ledger Server   │
│ SETTLEMENT 기록 │
└──────────────────┘
```

전체 흐름은 다음과 같습니다.

```text
결제 발생
   ↓
Payment DB 저장
   ↓
Ledger가 Payment API 조회
   ↓
신규 PAYMENT 이중분개 저장
   ↓
Settlement가 Ledger 조회
   ↓
기사별 정산 수행
   ↓
SETTLEMENT 분개 요청
   ↓
기사 미지급 잔액 감소
```

---

# 🔐 서비스 간 데이터 접근 원칙

EasyADJ의 각 서비스는 **자신의 데이터베이스만 직접 관리하며 다른 서비스의 DB에 직접 접근하지 않는 것**을 서비스 간 규약으로 합니다.

```text
Payment     → Payment DB만 직접 접근
Ledger      → Ledger DB만 직접 접근
Settlement  → Settlement DB만 직접 접근

서비스 간 데이터 교환 → HTTP API
```

따라서 Ledger 서비스에서 결제 데이터가 필요하더라도 Payment DB를 직접 조회하지 않습니다.

대신 Payment 서비스가 제공하는 API를 통해 필요한 데이터를 요청합니다.

```http
GET {PAYMENT_SERVER_BASE_URL}/api/ledger?driver_id={driverId}
```

이를 통해 각 서비스는 자신의 데이터에 대한 소유권을 유지하고, 다른 서비스는 공개된 API 계약을 통해서만 데이터에 접근하도록 구성했습니다.

또한 한 서비스의 DB 구조가 변경되더라도 API 계약이 유지된다면 다른 서비스에 미치는 영향을 줄일 수 있습니다.

## 왜 GET을 사용하는가?

현재 Payment → Ledger 연동에서는 Ledger가 Payment의 데이터를 **조회**하므로 HTTP Method의 의미에 따라 `GET`을 사용합니다.

```http
GET /api/ledger?driver_id=1
```

`driver_id`는 Query Parameter로 전달되기 때문에 URL이나 서버·프록시 로그 등에 기록될 가능성이 있습니다.

따라서 향후 개인정보나 인증정보와 같은 민감한 값을 전달해야 한다면 Query Parameter 사용 여부와 로그 정책을 별도로 검토해야 합니다.

다만 `POST`를 사용하여 값을 Request Body에 넣는다고 해서 데이터 자체가 자동으로 암호화되거나 안전해지는 것은 아닙니다.

전송 구간의 데이터 보호는 HTTPS를 사용하고, 실제 서비스에서는 추가적인 인증·인가 및 접근 제어를 적용해야 합니다.

현재 API는 **데이터 조회라는 의미와 HTTP Method의 일반적인 용도**를 고려하여 `GET`을 사용합니다.

> DB 독립성 규약이 반드시 Pull 방식을 의미하는 것은 아닙니다.  
> Payment가 Ledger API를 호출하는 Push 방식 역시 서로의 DB를 직접 접근하지 않는다면 동일한 규약을 만족할 수 있습니다. 현재 프로젝트에서는 API 기반 통신 원칙 아래 Ledger가 Payment를 조회하는 Pull 방식을 사용합니다.

---

# 🎯 Core Features

## 1. 결제 데이터 동기화

Ledger 서버가 Payment 서버의 API를 호출하여 기사별 결제 데이터를 가져옵니다.

```http
GET {PAYMENT_SERVER_BASE_URL}/api/ledger?driver_id={driverId}
```

Payment 서버 응답 예시:

```json
[
  {
    "paymentId": 2,
    "amount": 22000,
    "approvedAt": "2026-08-23T13:59:17"
  }
]
```

가져온 신규 결제는 Ledger DB에 `PAYMENT` 분개로 기록합니다.

```text
PAYMENT

PLATFORM  → DEBIT
DRIVER    → CREDIT
```

동일한 `driverId`, `paymentId`, `PAYMENT` 조합이 이미 존재하는 경우 다시 저장하지 않아 반복 동기화에 의한 잔액 증가를 방지합니다.

---

## 2. 이중기입 원장

하나의 거래를 `DEBIT`과 `CREDIT` 두 방향으로 기록합니다.

```text
DEBIT 합계 = CREDIT 합계
```

예를 들어 기사에게 15,000원의 미지급금이 발생하면:

```text
PLATFORM   DEBIT    15,000
DRIVER     CREDIT   15,000
```

으로 기록합니다.

하나의 요청에서 차변과 대변의 합계가 일치하지 않으면 분개를 저장하지 않습니다.

---

## 3. 기사별 미지급금 계산

기사의 현재 미지급금은 별도의 잔액 컬럼을 직접 수정하여 관리하지 않습니다.

원장에 기록된 분개의 합을 기반으로 계산합니다.

```text
기사 미지급금
= DRIVER CREDIT 합계
- DRIVER DEBIT 합계
```

예:

```text
PAYMENT
DRIVER CREDIT  15,000

PAYMENT
DRIVER CREDIT  20,000

SETTLEMENT
DRIVER DEBIT   25,000
────────────────────
현재 미지급금  10,000
```

따라서 잔액에 문제가 발생하더라도 기존 원장 기록을 이용해 계산 과정과 원인을 추적할 수 있습니다.

---

## 4. 결제 취소

결제가 취소되더라도 기존 `PAYMENT` 분개를 수정하거나 삭제하지 않습니다.

기존 결제와 반대 방향의 `PAYMENT_CANCEL` 분개를 추가합니다.

```text
PAYMENT
DRIVER CREDIT  15,000

        ↓ 결제 취소

PAYMENT_CANCEL
DRIVER DEBIT   15,000
```

이를 통해 원본 결제와 취소 기록을 모두 보존합니다.

---

## 5. 정산 지급 상쇄

기사에게 실제 정산금이 지급되면 `SETTLEMENT` 분개를 기록합니다.

```text
DRIVER    → DEBIT
PLATFORM  → CREDIT
```

`SETTLEMENT`은 특정 결제 한 건에 종속되는 분개가 아니므로 다음 규칙을 사용합니다.

```text
paymentId = null
```

---

## 6. 멱등성 및 중복 방지

서버 간 통신에서는 네트워크 오류 등으로 동일한 요청이 다시 전달될 수 있습니다.

Ledger는 이를 고려하여 중복 기록을 방지합니다.

### 분개 요청

`POST /api/ledger/entries`는 `Idempotency-Key`를 사용합니다.

```http
Idempotency-Key: settlement-1001
```

동일한 키의 요청이 다시 전달되면 새로운 분개를 생성하지 않고 기존 처리 결과를 반환합니다.

### Payment 동기화

Payment API를 반복해서 조회하더라도 이미 기록된 `driverId`, `paymentId`, `PAYMENT` 조합인지 확인하여 동일 결제가 다시 `PAYMENT` 분개로 생성되는 것을 방지합니다.

---

## 7. 실제 결제 승인 시각 보존

Payment 서버에서 전달된 `approvedAt`은 Ledger가 데이터를 가져온 시간이 아니라 **실제 결제가 승인된 시각**입니다.

```text
Payment approvedAt
        │
        ▼
Ledger approvedAt
```

이를 그대로 보존하여 기간별 정산 또는 결제 근거 조회 시 실제 결제 시각을 기준으로 처리할 수 있도록 했습니다.

`approvedAt`이 별도로 전달되지 않는 일반 원장 분개는 저장 시각을 기본값으로 사용합니다.

---

# 📒 Entry Type

| Entry Type | 설명 | DRIVER 기준 |
|---|---|---|
| `PAYMENT` | 결제 발생 | CREDIT |
| `PAYMENT_CANCEL` | 결제 취소에 따른 역분개 | DEBIT |
| `SETTLEMENT` | 정산 지급에 따른 상쇄 분개 | DEBIT |

---

# 💾 Ledger Data Model

`LedgerEntry`는 원장의 개별 분개를 저장합니다.

| Field | 설명 |
|---|---|
| `ledgerId` | 개별 분개의 식별자 |
| `driverId` | 기사 식별자 |
| `paymentId` | 결제 식별자 |
| `idempotencyKey` | 동일 거래의 중복 요청 방지를 위한 Key |
| `entryType` | PAYMENT / PAYMENT_CANCEL / SETTLEMENT |
| `direction` | DEBIT / CREDIT |
| `amount` | 분개 금액 |
| `approvedAt` | 결제 승인 또는 분개 기준 시각 |

`ownerType`은 API 요청 DTO에서 `DRIVER`, `PLATFORM` 분개를 구분하기 위해 사용하며, `LedgerEntry` 엔티티 자체에는 저장하지 않습니다. `ownerType=DRIVER`인 분개에만 `driverId`를 기록합니다.

금액은 `BigDecimal`을 사용하며 원 단위 정수로 저장합니다.

현재 주요 조회를 위해 다음 인덱스를 사용합니다.

```text
idempotency_key
driver_id + approved_at
approved_at
```

---

# 💰 금액 규약

## BigDecimal 사용

금액 계산에는 `BigDecimal`을 사용합니다.

```text
double / float 사용 X
BigDecimal 사용 O
```

부동소수점 오차가 원장 금액에 영향을 주지 않도록 하기 위함입니다.

## 원 단위 정수

Ledger에 기록되는 금액은 1원 단위 정수만 허용합니다.

허용:

```json
{
  "amount": "15000"
}
```

허용하지 않음:

```json
{
  "amount": "15000.5"
}
```

수수료 또는 지급액 계산 과정에서 소수가 발생하는 경우 계산을 담당하는 서비스에서 합의된 라운딩 규칙을 적용한 후 Ledger에 전달해야 합니다.

## JSON 금액 표현

서비스 간 원장 API에서 금액은 JSON String 형태로 표현합니다.

```json
{
  "totalUnpaidAmount": "251000"
}
```

---

# 📡 API

## Base URL

```text
https://driver-ledger-system-production.up.railway.app
```

Base Path:

```text
/api/ledger
```

## API Summary

| Method | Endpoint | 설명 |
|---|---|---|
| `POST` | `/api/ledger/sync` | Payment 결제 데이터 동기화 |
| `POST` | `/api/ledger/entries` | 원장 분개 기록 |
| `GET` | `/api/ledger` | 기사별 원장 및 미지급금 조회 |
| `GET` | `/api/ledger/unpaid` | 미지급 기사 목록 조회 |
| `GET` | `/api/ledger/verify` | DEBIT/CREDIT 정합성 검증 |

---

## 1. 결제 데이터 동기화

```http
POST /api/ledger/sync?driver_id=1
```

Ledger가 Payment 서버의 기사별 결제 내역을 조회하고 아직 원장에 존재하지 않는 결제를 저장합니다.

### Response

```json
{
  "syncedCount": 5
}
```

신규 데이터가 없다면:

```json
{
  "syncedCount": 0
}
```

---

## 2. 분개 기록

```http
POST /api/ledger/entries
```

결제 취소 및 정산 지급 등 서버 간 원장 분개를 기록합니다.

### Header

```http
Idempotency-Key: settlement-1001
Content-Type: application/json
```

### PAYMENT Example

```json
{
  "driverId": 1,
  "entryType": "PAYMENT",
  "entries": [
    {
      "direction": "DEBIT",
      "amount": "15000",
      "paymentId": 1001,
      "ownerType": "PLATFORM"
    },
    {
      "direction": "CREDIT",
      "amount": "15000",
      "paymentId": 1001,
      "ownerType": "DRIVER"
    }
  ]
}
```

### SETTLEMENT Example

```json
{
  "driverId": 1,
  "entryType": "SETTLEMENT",
  "entries": [
    {
      "direction": "DEBIT",
      "amount": "5000",
      "paymentId": null,
      "ownerType": "DRIVER"
    },
    {
      "direction": "CREDIT",
      "amount": "5000",
      "paymentId": null,
      "ownerType": "PLATFORM"
    }
  ]
}
```

### Validation

- `DEBIT` 합계와 `CREDIT` 합계가 일치해야 합니다.
- 금액은 1원 단위 정수여야 합니다.
- `Idempotency-Key`를 이용해 중복 요청을 방어합니다.
- `ownerType=DRIVER`인 분개에 기사 식별자를 기록합니다.
- `SETTLEMENT.paymentId`는 `null`이어야 합니다.

### Response

```http
201 Created
```

```json
{
  "ledgerId": 1
}
```

동일한 `Idempotency-Key`가 다시 전달되면 신규 분개를 생성하지 않고 기존 처리 결과를 반환합니다.

---

## 3. 기사별 원장 조회

전체 결제 근거 조회:

```http
GET /api/ledger?driver_id=1
```

기간별 조회:

```http
GET /api/ledger?driver_id=1&from=2026-08-23&to=2026-08-23
```

`from`과 `to`는 함께 지정해야 합니다.

조회 범위는 다음과 같습니다.

```text
from 00:00:00 이상
~
to 다음 날 00:00:00 미만
```

따라서 `from=2026-08-23&to=2026-08-23`이면 8월 23일 하루 동안 승인된 결제 근거를 조회합니다.

### Response

```json
{
  "driverId": 1,
  "totalUnpaidAmount": "251000",
  "paymentDetails": [
    {
      "paymentId": 2,
      "amount": "22000",
      "approvedAt": "2026-08-23T13:59:17",
      "entryType": "PAYMENT"
    }
  ]
}
```

`paymentDetails`에는 `PAYMENT`와 `PAYMENT_CANCEL` 근거가 포함될 수 있으며 `entryType`으로 구분합니다.

> `from`, `to`는 `paymentDetails`의 조회 기간만 제한합니다.  
> `totalUnpaidAmount`는 기간과 관계없이 현재 기사의 전체 미지급 잔액입니다.

---

## 4. 미지급 기사 목록 조회

```http
GET /api/ledger/unpaid?date=2026-08-23
```

지정한 날짜까지의 원장 기록을 기준으로 미지급 잔액이 존재하는 기사 목록을 조회합니다.

미지급 잔액이 `0`보다 큰 기사만 반환합니다.

### Response

```json
{
  "targetDate": "2026-08-23",
  "data": [
    {
      "driverId": 1,
      "totalUnpaidAmount": "251000",
      "lastApprovedAt": "2026-08-23T13:59:17"
    }
  ]
}
```

---

## 5. 원장 정합성 검증

```http
GET /api/ledger/verify?from=2026-08-23&to=2026-08-23
```

지정한 기간의 전체 분개에 대해 `DEBIT`과 `CREDIT` 합계가 일치하는지 검증합니다.

정상:

```http
200 OK
```

정합성 불일치:

```http
409 Conflict
```

잘못된 날짜 범위:

```http
400 Bad Request
```

---

# 🚨 Error Response

공통 오류 응답 예시:

```json
{
  "code": "INVALID_REQUEST",
  "message": "요청 본문 형식이 올바르지 않습니다.",
  "transactionId": null
}
```

| Status | 의미 |
|---|---|
| `400 Bad Request` | 잘못된 요청 |
| `409 Conflict` | 원장 정합성 불일치 |
| `500 Internal Server Error` | 처리되지 않은 서버 오류 |

---

# 🔑 Design Principles

## 1. 서비스별 DB 독립성

각 서비스는 자신의 DB만 직접 관리합니다.

```text
다른 서비스 DB 직접 조회 X
HTTP API를 통한 데이터 교환 O
```

이를 통해 서비스 간 데이터 저장 구조에 대한 직접적인 의존성을 줄입니다.

---

## 2. Ledger를 미지급금 계산의 Source of Truth로 사용

기사의 현재 미지급금을 별도의 숫자로 직접 관리하지 않습니다.

```text
현재 미지급금 = 원장 분개의 합
```

원장 기록을 기반으로 현재 상태를 계산합니다.

---

## 3. 원본 기록 보존

결제 취소나 정산 지급이 발생하더라도 기존 원장 기록을 수정하거나 삭제하지 않습니다.

```text
기존 기록 수정 X
기존 기록 삭제 X

역분개 / 상쇄 분개 추가 O
```

이를 통해 거래의 변경 이력을 추적할 수 있도록 합니다.

---

## 4. 재시도 가능한 API

분산 환경에서는 네트워크 문제로 동일 요청이 다시 전달될 수 있다고 가정합니다.

따라서:

```text
서버 간 분개 요청
→ Idempotency-Key

결제 동기화
→ driverId + paymentId + PAYMENT 존재 여부 확인
```

방식으로 중복 기록을 방지합니다.

---

## 5. 실제 거래 시각 보존

Ledger가 Payment 데이터를 가져온 시간이 아니라 Payment에서 발생한 실제 `approvedAt`을 보존합니다.

이를 통해 기간 기반 조회와 정산의 기준이 동기화 실행 시점에 따라 달라지는 것을 방지합니다.

---

# 🛠 Tech Stack

| 구분 | 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1.0 |
| Web | Spring Web |
| HTTP Client | Spring RestClient |
| ORM | Spring Data JPA |
| Production DB | PostgreSQL / Supabase |
| Test DB | H2 |
| Build | Gradle Kotlin DSL |
| Utility | Lombok |
| Test | JUnit 5 / Spring Boot Test |
| Deployment | Railway |

---

# 📂 Project Structure

```text
src/main/java/com/example/driverledgersystem
├── client
│   └── PaymentClient.java
├── controller
│   └── LedgerController.java
├── domain
│   ├── Direction.java
│   └── EntryType.java
├── dto
│   ├── DriverLedgerResponse.java
│   ├── ErrorResponse.java
│   ├── LedgerEntryRequest.java
│   ├── PaymentLedgerResponse.java
│   └── UnpaidDriverListResponse.java
├── entity
│   └── LedgerEntry.java
├── exception
│   ├── GlobalExceptionHandler.java
│   └── ...
├── repository
│   └── LedgerEntryRepository.java
├── service
│   ├── LedgerService.java
│   └── PaymentSyncService.java
└── LedgerSystemApplication.java
```

---

# ⚙️ Getting Started

## Requirements

```text
Java 21
PostgreSQL
```

## Spring Profile

Local:

```properties
SPRING_PROFILES_ACTIVE=local
```

Production:

```properties
SPRING_PROFILES_ACTIVE=prod
```

## Environment Variables

```properties
SPRING_PROFILES_ACTIVE=prod

SPRING_DATASOURCE_URL=<PostgreSQL JDBC URL>
SPRING_DATASOURCE_USERNAME=<Database Username>
SPRING_DATASOURCE_PASSWORD=<Database Password>

PAYMENT_SERVER_BASE_URL=<Payment Server URL>
```

DB 비밀번호 등의 실제 자격 증명은 저장소에 커밋하지 않습니다.

---

# ▶️ Run

### Windows

```powershell
.\gradlew.bat bootRun
```

### Linux / macOS

```bash
./gradlew bootRun
```

---

# 🧪 Test & Build

## Test

Windows:

```powershell
.\gradlew.bat clean test
```

Linux / macOS:

```bash
./gradlew clean test
```

## Build

Windows:

```powershell
.\gradlew.bat clean bootJar
```

Linux / macOS:

```bash
./gradlew clean bootJar
```

---

# 🚀 Deployment

운영 환경은 Railway를 사용합니다.

```powershell
railway up
```

배포 환경에서는 `prod` Profile을 사용합니다.

---

# ✅ Integration Test

Payment · Ledger · Settlement 서버를 실제 배포 환경에서 연결하여 전체 흐름을 검증했습니다.

```text
Payment
   │
   │ 결제 생성
   ▼
Payment DB
   │
   │ Ledger가 API 조회
   ▼
Ledger
   │
   │ PAYMENT 분개
   ▼
Settlement
   │
   │ 정산 및 지급
   ▼
Ledger
   │
   │ SETTLEMENT 상쇄
   ▼
미지급 잔액 감소 확인
```

주요 검증 항목:

- Payment → Ledger 실제 HTTP 통신
- 결제 데이터 동기화
- 동일 결제 중복 동기화 방지
- `PAYMENT` 이중분개
- `PAYMENT_CANCEL` 역분개
- `SETTLEMENT` 상쇄 분개
- 실제 `approvedAt` 보존
- 기간별 결제 근거 조회
- `Idempotency-Key` 기반 중복 방지
- 금액 JSON String 직렬화
- DEBIT / CREDIT 정합성 검증
- Railway 환경 서비스 간 통신
- PostgreSQL 저장 및 조회

---

# ⚠️ Current Limitations & Future Work

현재 시스템은 **Payment → Ledger → Settlement의 핵심 흐름과 이중기입 원장 구조를 구현하고 검증하는 것**에 초점을 두고 있습니다.

실제 대규모 운영 환경으로 확장할 경우 다음 사항에 대한 추가적인 고려가 필요합니다.

## 1. Pull 방식의 결제 동기화

현재 Ledger가 Payment API를 조회하여 결제 데이터를 가져오는 Pull 방식을 사용합니다.

```text
Ledger
   │
   │ GET
   ▼
Payment API
```

이 방식에서도 각 서비스의 DB 독립성은 유지됩니다.

다만 Ledger가 동기화를 수행하기 전까지 Payment에 존재하는 최신 결제가 Ledger에 반영되지 않을 수 있습니다.

향후 실시간성이 중요해진다면 DB 독립성 원칙은 유지하면서 Event-driven 구조를 고려할 수 있습니다.

```text
Payment
   │
   │ Payment Created Event
   ▼
Message Broker
   │
   ▼
Ledger
```

예:

- Kafka
- RabbitMQ
- Transactional Outbox Pattern

향후 개선의 목적은 **DB를 공유하는 것**이 아니라 Pull 방식에서 발생할 수 있는 동기화 지연과 장애 복구 문제를 개선하는 것입니다.

---

## 2. 분산 트랜잭션

Payment, Ledger, Settlement는 각각 독립적인 DB를 사용하므로 하나의 DB Transaction으로 전체 작업을 묶을 수 없습니다.

예를 들어:

```text
Payment 결제 성공
       ↓
Payment DB 저장 성공
       ↓
Ledger 반영 실패
```

와 같은 상황이 발생할 수 있습니다.

현재 구조에서는 재동기화 및 중복 방지 기능을 이용해 이러한 문제를 완화할 수 있지만, 운영 환경에서는 보다 명확한 장애 복구 전략이 필요합니다.

향후 고려할 수 있는 방법:

- Retry 정책
- Transactional Outbox
- Dead Letter Queue
- Saga Pattern
- 주기적인 서비스 간 데이터 정합성 검증

---

## 3. 동시성 제어

현재 Payment 동기화의 중복 방지는 먼저 기존 데이터 존재 여부를 확인한 뒤 저장하는 방식입니다.

```text
존재 여부 확인
      ↓
     없음
      ↓
    INSERT
```

여러 Ledger 인스턴스가 동시에 같은 결제를 동기화한다면 다음과 같은 Race Condition이 발생할 가능성이 있습니다.

```text
Instance A → 존재 여부 확인 → 없음
Instance B → 존재 여부 확인 → 없음

Instance A → INSERT
Instance B → INSERT
```

향후에는 다음과 같은 방식을 고려할 수 있습니다.

- 거래 단위 Database Constraint 설계
- Atomic INSERT / UPSERT
- Optimistic Lock
- Pessimistic Lock
- 필요 시 Distributed Lock

단, 현재 하나의 거래가 `DEBIT`, `CREDIT` 두 개 이상의 `LedgerEntry`를 생성하고 동일한 `idempotencyKey`를 공유하므로, `idempotency_key` 컬럼 자체에 단순 UNIQUE 제약을 추가하는 방식은 현재 모델과 맞지 않습니다.

---

## 4. Idempotency-Key 정책 고도화

현재 `POST /api/ledger/entries`는 동일한 `Idempotency-Key`가 존재하면 기존 처리 결과의 `ledgerId`를 반환합니다.

하지만 현재 구현은 **동일 Key로 들어온 요청의 Body까지 동일한지 추가 검증하지 않습니다.**

따라서 다음과 같은 요청이 발생할 가능성을 고려해야 합니다.

```text
Idempotency-Key: settlement-1001
amount: 10000

        ↓ 재요청

Idempotency-Key: settlement-1001
amount: 20000
```

향후에는 다음을 고려할 수 있습니다.

- Request Body Hash 저장 및 비교
- 동일 Key + 다른 Payload 요청을 Conflict로 처리
- Key 유효 기간
- Key 보관 및 정리 정책
- 거래 단위 멱등성 관리 테이블

---

## 5. 거래와 개별 분개 모델의 분리

현재 하나의 거래는 여러 개의 `LedgerEntry`로 표현되며, 같은 거래에 속한 분개들이 동일한 `idempotencyKey`를 공유합니다.

```text
idempotencyKey = payment-import-100

├─ PLATFORM / DEBIT
└─ DRIVER   / CREDIT
```

따라서 현재 구조에서는 `idempotency_key` 하나만으로 거래 자체를 독립적인 엔티티처럼 관리하기 어렵습니다.

향후 시스템이 확장된다면 거래 단위 정보와 개별 분개를 분리하는 구조를 고려할 수 있습니다.

```text
LedgerTransaction
├─ transactionId
├─ idempotencyKey
├─ entryType
└─ createdAt
        │
        └── LedgerEntry
            ├─ DEBIT
            └─ CREDIT
```

이 구조를 사용하면:

- 거래 단위 Idempotency UNIQUE 제약
- 거래 상태 관리
- 요청 Payload Hash 저장
- 거래 단위 조회
- 여러 분개 간 관계 표현

등을 보다 명확하게 처리할 수 있습니다.

---

## 6. 원장 불변성 강화

현재 설계에서는 기존 원장 기록을 수정하거나 삭제하지 않는 것을 원칙으로 합니다.

하지만 실제 운영 환경에서는 이 규칙을 애플리케이션 코드뿐만 아니라 데이터베이스와 운영 정책 수준에서도 강화할 수 있습니다.

향후 고려 사항:

- Ledger Entry UPDATE 제한
- Ledger Entry DELETE 제한
- DB 사용자 권한 분리
- 관리자 작업 Audit Log
- 원장 변경 작업 추적

---

## 7. 단순화된 원장 모델

현재 시스템은 EasyADJ 프로젝트에서 필요한 결제 및 정산 흐름을 표현하는 데 초점을 맞춘 원장 모델입니다.

```text
PAYMENT
PAYMENT_CANCEL
SETTLEMENT
```

실제 복잡한 회계 시스템으로 확장한다면 보다 세분화된 Account 체계가 필요할 수 있습니다.

예:

```text
DRIVER_PAYABLE
PLATFORM_REVENUE
PAYMENT_RECEIVABLE
SETTLEMENT_CLEARING
REFUND
FEE
```

따라서 현재 Ledger는 범용 회계 시스템이라기보다 **EasyADJ의 결제 및 정산 흐름을 기록하기 위한 도메인 원장**으로 보는 것이 적절합니다.

---

## 8. 정산 금액 계산 책임

Ledger는 수수료율이나 실제 지급액을 직접 계산하는 서비스가 아닙니다.

다른 서비스에서 계산된 금액을 전달받아 분개하고 기록하는 역할에 집중합니다.

따라서 서비스 간 다음 규약이 일치해야 합니다.

- 수수료율
- 지급액 계산 방식
- 반올림 정책
- 금액 단위
- BigDecimal Scale
- JSON 금액 표현 방식

이러한 규칙이 서비스마다 다르면 Ledger 자체의 DEBIT/CREDIT 정합성이 맞더라도 비즈니스 금액이 서로 달라질 수 있습니다.

따라서 금액 계산 규칙은 서비스 간 공통 계약으로 관리하는 것이 필요합니다.

---

## 9. 서버 간 인증 및 접근 제어

현재 프로젝트는 서비스 간 기능 연동과 Ledger 로직 검증에 초점을 두고 있습니다.

실제 운영 수준으로 확장한다면 내부 API에 대한 인증 및 권한 제어가 필요합니다.

특히:

```http
POST /api/ledger/entries
```

와 같이 원장 데이터를 생성하는 API는 허가된 서비스만 호출할 수 있도록 제한해야 합니다.

향후 고려 사항:

- Service-to-Service Authentication
- API Key
- JWT
- mTLS
- Request Signature
- Role 기반 접근 제어
- 내부 API / 외부 API 분리

---

## 10. Observability

여러 서비스가 HTTP로 통신하기 때문에 장애 발생 시 하나의 거래가 어느 서비스에서 실패했는지 추적하는 기능이 중요합니다.

향후 다음과 같은 기능을 고려할 수 있습니다.

- Trace ID
- Correlation ID
- Structured Logging
- OpenTelemetry
- Prometheus / Grafana
- Error Monitoring

예를 들어 하나의 `paymentId` 또는 `transactionId`를 이용해:

```text
Payment
   ↓
Ledger
   ↓
Settlement
   ↓
Ledger
```

전체 요청 흐름을 추적할 수 있도록 개선할 수 있습니다.

---

## 11. 대용량 원장 데이터 처리

Ledger 데이터는 거래가 발생할 때마다 지속적으로 증가합니다.

현재 구현에는 주요 조회 조건에 대한 인덱스가 적용되어 있지만, 데이터 규모가 크게 증가하면 단순한 원장 집계만으로는 조회 비용이 커질 수 있습니다.

특히:

```text
기사별 현재 미지급금 계산
기간별 결제 내역 조회
전체 DEBIT / CREDIT 정합성 검증
```

등의 작업은 데이터 증가에 따라 추가적인 최적화가 필요할 수 있습니다.

향후 고려 사항:

- Query 최적화
- 추가 Index 설계
- Pagination
- Batch 처리
- Summary Table
- Snapshot
- Table Partitioning
- 오래된 Ledger 데이터 Archive

Summary 또는 Snapshot을 사용하더라도 원본 `LedgerEntry`를 거래 이력의 근거로 유지하는 것이 중요합니다.

---

# 🧭 Recommended Improvement Roadmap

향후 이 프로젝트를 이어서 개발한다면 다음 순서로 개선하는 것을 권장합니다.

```text
1. 거래 단위 멱등성 및 동시성 제어 강화
        ↓
2. 서버 간 인증 및 접근 제어
        ↓
3. Retry / 장애 복구 정책
        ↓
4. Payment → Ledger 이벤트 기반 연동 검토
        ↓
5. Trace ID 기반 분산 요청 추적
        ↓
6. 대용량 Ledger 조회 최적화
        ↓
7. Account / Ledger 모델 확장
```

현재 구조를 변경할 때에도 다음 세 가지 원칙은 유지하는 것을 권장합니다.

### ① 서비스별 DB 독립성

```text
다른 서비스 DB 직접 접근 X
정의된 API / Event를 통한 통신 O
```

### ② 원장 기록 보존

```text
기존 Ledger 수정·삭제 X
역분개 또는 상쇄 분개 추가 O
```

### ③ 재시도 안전성

```text
네트워크 요청은 중복될 수 있다는 것을 전제로 설계
```

---

# 📌 Summary

Driver Ledger System은 EasyADJ의 Payment와 Settlement 사이에서 거래 기록과 미지급금의 근거를 관리하는 서비스입니다.

```text
Payment
   ↓
PAYMENT
   ↓
Ledger
   ↓
Settlement
   ↓
SETTLEMENT
   ↓
Ledger
```

핵심적으로 다음 원칙을 적용했습니다.

- 서비스별 독립 DB
- HTTP API 기반 서비스 간 통신
- Double-entry Ledger
- DEBIT / CREDIT 정합성 검증
- 기존 거래 수정 대신 역분개
- Ledger를 미지급금 계산의 Source of Truth로 사용
- Idempotency-Key 기반 중복 요청 방지
- Payment ID 기반 중복 동기화 방지
- 실제 결제 승인 시각 보존
- BigDecimal 기반 금액 처리

현재 구현은 EasyADJ의 결제 → 원장 → 정산 흐름을 구현하고 검증하는 데 초점을 맞추고 있으며, 향후 이벤트 기반 통신, 장애 복구, 인증·인가, 동시성 제어, 거래 단위 멱등성 관리 및 대용량 처리 등을 추가하여 확장할 수 있습니다.
