# 📚 Driver Ledger System

EasyADJ 모빌리티 정산 플랫폼의 **원장(Ledger) 서비스**입니다.

결제(Payment) 서비스에서 발생한 결제 데이터를 조회하여 **이중기입 원장(Double-entry Ledger)** 방식으로 기록하고, 정산(Settlement) 서비스가 기사별 미지급금과 결제 근거를 조회할 수 있도록 제공합니다.

원장 서비스는 결제와 정산 사이에서 금액의 정합성을 유지하고, 결제 발생부터 취소·정산 지급까지의 변경 이력을 원장 기록으로 추적할 수 있도록 하는 것을 목표로 합니다.

---

# 🏗 서비스 흐름

현재 시스템은 **결제 · 원장 · 정산 3개 서버가 분리**되어 있으며 각 서비스가 독립적인 DB를 사용합니다.

```text
Payment Server
      │
      │ GET /api/ledger?driver_id=
      ▼
Ledger Server
      │
      ├─ 신규 결제 확인
      ├─ PAYMENT 이중분개 생성
      ├─ 실제 결제 승인 시각 보존
      └─ Ledger DB 저장
      │
      │ GET /api/ledger
      │ GET /api/ledger/unpaid
      ▼
Settlement Server
      │
      │ 정산 완료
      │ POST /api/ledger/entries
      ▼
Ledger Server
      │
      └─ SETTLEMENT 상쇄 분개 기록
```

### 결제 → 원장

1. 결제 서버가 자신의 DB에 결제 데이터를 저장합니다.
2. 원장 서버가 결제 서버의 기사별 결제 내역 API를 호출합니다.
3. 결제 서버가 `paymentId`, `amount`, `approvedAt`을 반환합니다.
4. 원장은 이미 기록된 `paymentId`인지 확인합니다.
5. 신규 결제만 `PAYMENT` 이중분개로 원장 DB에 저장합니다.
6. 결제 서버가 전달한 실제 `approvedAt`을 원장에서도 그대로 보존합니다.

### 원장 → 정산

1. 정산 서버가 원장 서버에서 정산 대상 기사와 결제 근거를 조회합니다.
2. 원장 서버는 분개 합계를 기반으로 기사별 미지급금을 계산합니다.
3. 정산 서버가 정산을 수행합니다.
4. 지급 완료 후 정산 서버가 원장에 `SETTLEMENT` 분개를 요청합니다.
5. 원장은 지급된 금액만큼 기사 미지급 잔액을 상쇄합니다.

---

# 🎯 주요 역할

## 1. 결제 데이터 동기화

원장 서버가 결제 서버의 API를 호출하여 기사별 결제 데이터를 가져옵니다.

```http
GET {PAYMENT_SERVER_BASE_URL}/api/ledger?driver_id={driverId}
```

결제 서버 응답:

```json
[
  {
    "paymentId": 2,
    "amount": 22000,
    "approvedAt": "2026-08-23T13:59:17"
  }
]
```

가져온 데이터는 원장 DB에 다음과 같이 기록됩니다.

```text
PAYMENT

PLATFORM → DEBIT
DRIVER   → CREDIT
```

동일한 `driverId`, `paymentId`, `PAYMENT` 조합이 이미 존재하는 경우 다시 저장하지 않습니다.

---

## 2. 이중기입 분개

하나의 거래를 `DEBIT`과 `CREDIT` 두 방향으로 기록합니다.

```text
DEBIT 합계 = CREDIT 합계
```

하나의 요청에서 차변과 대변의 합계가 일치하지 않으면 저장하지 않습니다.

이를 통해 하나의 거래가 원장의 전체 금액을 임의로 증가시키거나 감소시키지 않도록 합니다.

---

## 3. 멱등성 및 중복 방지

원장은 두 가지 방식으로 중복 기록을 방지합니다.

### 서버 간 분개 요청

`POST /api/ledger/entries`는 `Idempotency-Key`를 사용합니다.

동일한 키로 요청이 다시 들어오면 새로운 분개를 생성하지 않고 기존 처리 결과를 반환합니다.

### 결제 데이터 동기화

결제 서버에서 동일한 결제 목록을 반복 조회하더라도 이미 원장에 존재하는 `paymentId`의 `PAYMENT` 분개는 다시 생성하지 않습니다.

따라서 동일 결제 데이터를 여러 번 동기화해도 원장 잔액은 증가하지 않습니다.

---

## 4. 기사별 미지급금 계산

기사의 미지급 잔액은 별도의 잔액 컬럼을 직접 갱신하지 않고 원장 분개의 합으로 계산합니다.

```text
미지급 잔액
= DRIVER CREDIT 합계
- DRIVER DEBIT 합계
```

이를 통해 잔액에 문제가 발생하더라도 원장 이력을 통해 원인을 추적할 수 있습니다.

---

## 5. 결제 취소

결제가 취소되는 경우 기존 결제 기록을 수정하거나 삭제하지 않습니다.

기존 결제와 반대 방향의 `PAYMENT_CANCEL` 분개를 추가하여 기사 미지급 잔액을 감소시킵니다.

```text
PAYMENT
DRIVER CREDIT

        ↓ 취소

PAYMENT_CANCEL
DRIVER DEBIT
```

원본과 취소 기록이 모두 남기 때문에 변경 이력을 추적할 수 있습니다.

---

## 6. 정산 지급 상쇄

기사에게 실제 정산금이 지급되면 `SETTLEMENT` 분개를 기록합니다.

```text
DRIVER   → DEBIT
PLATFORM → CREDIT
```

이를 통해 지급된 금액만큼 기사 미지급 잔액이 감소합니다.

`SETTLEMENT`은 특정 결제 한 건에 종속되지 않으므로:

```text
paymentId = null
```

규칙을 사용합니다.

---

## 7. 실제 결제 승인 시각 보존

결제 서버에서 가져온 `approvedAt`은 원장 동기화 시각으로 변경하지 않고 그대로 저장합니다.

```text
Payment approvedAt
        ↓
Ledger approvedAt
```

이를 통해 정산 서버가 기간별 결제 내역을 조회할 때 **원장에 데이터를 가져온 시각이 아니라 실제 결제 승인 시각**을 기준으로 처리할 수 있습니다.

`approvedAt`이 별도로 전달되지 않는 원장 분개는 저장 시각을 기본값으로 사용합니다.

---

## 8. 금액 정밀도

모든 금액 계산에는 `BigDecimal`을 사용합니다.

원장에 기록되는 금액은 **1원 단위 정수만 허용**합니다.

서비스 간 원장 API의 금액 응답은 JSON String 형태를 사용합니다.

```json
{
  "totalUnpaidAmount": "251000"
}
```

---

## 9. 원장 정합성 검증

특정 기간의 전체 분개에 대해 `DEBIT`과 `CREDIT`이 일치하는지 검증할 수 있습니다.

정상:

```http
200 OK
```

불일치:

```http
409 Conflict
```

---

# 🛠 Tech Stack

| 구분 | 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1.0 |
| Web | Spring Web |
| HTTP Client | Spring RestClient |
| ORM | Spring Data JPA |
| Production Database | PostgreSQL / Supabase |
| Test Database | H2 |
| Build | Gradle Kotlin DSL |
| Utility | Lombok |
| Test | JUnit 5 / Spring Boot Test |
| Deployment | Railway |

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

---

## 1. 결제 데이터 동기화

```http
POST /api/ledger/sync?driver_id=1
```

원장 서버가 결제 서버의 기사별 결제 내역을 조회하고, 아직 원장에 기록되지 않은 결제를 `PAYMENT` 분개로 저장합니다.

### Response

```json
{
  "syncedCount": 5
}
```

`5`는 이번 요청에서 새롭게 저장된 결제 건수입니다.

같은 결제를 다시 동기화하면:

```json
{
  "syncedCount": 0
}
```

이 반환됩니다.

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

### PAYMENT Request Example

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

### SETTLEMENT Request Example

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

### 검증 규칙

- `DEBIT` 합계와 `CREDIT` 합계가 일치해야 합니다.
- 금액은 1원 단위 정수여야 합니다.
- `Idempotency-Key`를 기반으로 중복 요청을 방어합니다.
- `ownerType=DRIVER`인 분개에 `driverId`를 기록합니다.
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

동일한 `Idempotency-Key`가 다시 전달되면 새로운 분개를 만들지 않고 기존 `ledgerId`를 반환합니다.

---

## 3. 기사별 원장 조회

전체 결제 근거:

```http
GET /api/ledger?driver_id=1
```

기간별 결제 근거:

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

즉 `from=2026-08-23&to=2026-08-23`이면 8월 23일 하루 동안 승인된 결제 근거가 반환됩니다.

### Response Example

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

`paymentDetails`에는 `PAYMENT` 및 `PAYMENT_CANCEL` 근거가 포함될 수 있으며 `entryType`으로 구분합니다.

> `from`, `to`는 `paymentDetails`의 조회 범위를 제한합니다. `totalUnpaidAmount`는 현재 기사 미지급 잔액입니다.

---

## 4. 미지급 기사 목록 조회

```http
GET /api/ledger/unpaid?date=2026-08-23
```

지정한 날짜까지 원장 기록을 기준으로 미지급 잔액이 존재하는 기사 목록을 조회합니다.

미지급 잔액이 **0보다 큰 기사만** 반환합니다.

### Response Example

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

지정한 기간의 전체 분개에 대해 차변과 대변의 합계를 검증합니다.

정상:

```http
200 OK
```

정합성 불일치:

```http
409 Conflict
```

`from`이 `to`보다 이후인 경우:

```http
400 Bad Request
```

---

# 📒 Entry Type

| Entry Type | 설명 |
|---|---|
| `PAYMENT` | 결제 발생에 따른 분개 |
| `PAYMENT_CANCEL` | 결제 취소에 따른 역분개 |
| `SETTLEMENT` | 정산 지급 완료에 따른 상쇄 분개 |

---

# 💰 금액 규약

## 내부 계산

금액 계산에는 `BigDecimal`을 사용합니다.

`double`, `float`은 금액 계산에 사용하지 않습니다.

## 원 단위

원장에는 1원 단위 정수만 기록합니다.

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

수수료나 지급액 계산 과정에서 소수가 발생하면 계산을 담당하는 서비스에서 합의된 라운딩 규칙을 적용한 후 원장에 전달해야 합니다.

---

# 🚨 오류 응답

공통 오류 응답:

```json
{
  "code": "INVALID_REQUEST",
  "message": "요청 본문 형식이 올바르지 않습니다.",
  "transactionId": null
}
```

잘못된 요청:

```http
400 Bad Request
```

원장 정합성 불일치:

```http
409 Conflict
```

처리되지 않은 서버 오류:

```http
500 Internal Server Error
```

---

# ⚙️ 실행 방법

## 요구 환경

```text
Java 21
PostgreSQL
```

## Spring Profile

로컬:

```properties
SPRING_PROFILES_ACTIVE=local
```

운영:

```properties
SPRING_PROFILES_ACTIVE=prod
```

---

## 환경 변수

```properties
SPRING_PROFILES_ACTIVE=prod

SPRING_DATASOURCE_URL=<PostgreSQL JDBC URL>
SPRING_DATASOURCE_USERNAME=<Database Username>
SPRING_DATASOURCE_PASSWORD=<Database Password>

PAYMENT_SERVER_BASE_URL=https://driver-payment-system-production.up.railway.app
```

DB 비밀번호와 같은 실제 자격 증명은 저장소에 커밋하지 않습니다.

`PAYMENT_SERVER_BASE_URL`은 원장이 결제 서버의 결제 내역 API를 호출할 때 사용합니다.

---

## 실행

### Windows

```powershell
.\gradlew.bat bootRun
```

### Linux / macOS

```bash
./gradlew bootRun
```

---

# 🧪 테스트 및 빌드

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

# 🚀 배포

운영 환경은 Railway를 사용합니다.

현재 배포는 Railway CLI를 통해 수행합니다.

```powershell
railway up
```

배포 환경에서는 다음 Profile을 사용합니다.

```text
prod
```

---

# ✅ 통합 테스트

결제 · 원장 · 정산 3개 서버를 실제 배포 환경에서 연결하여 전체 통합 테스트를 완료했습니다.

검증 흐름:

```text
결제 생성
   ↓
Payment DB 저장
   ↓
Ledger가 Payment API 조회
   ↓
신규 PAYMENT 이중분개 저장
   ↓
정산 서버가 Ledger 조회
   ↓
기사별 정산 수행
   ↓
SETTLEMENT 분개 요청
   ↓
기사 미지급 잔액 감소
   ↓
원장 정합성 검증
```

검증 항목:

- 결제 서버 → 원장 서버 실제 HTTP 통신
- 결제 데이터 원장 동기화
- `paymentId` 기반 중복 동기화 방지
- 동일 데이터 재동기화 시 신규 분개 미생성
- `PAYMENT` 이중분개 저장
- 실제 결제 `approvedAt` 보존
- 기간별 `paymentDetails` 조회
- `PAYMENT_CANCEL` 역분개 반영
- 정산 서버의 기사별 미지급금 및 결제 근거 조회
- `SETTLEMENT` 상쇄 분개 반영
- 정산 후 기사 미지급 잔액 감소
- `SETTLEMENT.paymentId = null`
- `Idempotency-Key` 기반 중복 분개 방지
- 금액 JSON String 직렬화
- 전체 `DEBIT` / `CREDIT` 정합성 검증
- Railway 운영 환경 통신
- Supabase PostgreSQL 저장 및 조회

전체 통합 테스트에서 결제 → 원장 → 정산 → 원장 상쇄 흐름이 정상 동작함을 확인했습니다.

---

# 📂 주요 프로젝트 구조

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
│   ├── LedgerEntryRequest.java
│   ├── PaymentLedgerResponse.java
│   └── UnpaidDriverListResponse.java
├── entity
│   └── LedgerEntry.java
├── exception
│   └── GlobalExceptionHandler.java
├── repository
│   └── LedgerEntryRepository.java
├── service
│   ├── LedgerService.java
│   └── PaymentSyncService.java
└── LedgerSystemApplication.java
```

---

# 🔑 설계 원칙

## 원장을 Source of Truth로 사용

기사의 현재 미지급 잔액을 별도의 숫자로 직접 관리하지 않습니다.

```text
현재 잔액 = 원장 분개의 합
```

으로 계산합니다.

## 원본 기록 보존

결제 취소나 정산 지급이 발생해도 기존 분개를 수정하거나 삭제하지 않습니다.

새로운 역분개 또는 상쇄 분개를 추가하여 전체 변경 이력을 남깁니다.

## 재시도 안전성

서버 간 통신은 실패 후 재시도될 수 있다는 전제하에 설계합니다.

- 분개 요청 → `Idempotency-Key`
- 결제 동기화 → 기존 `paymentId` 확인

을 통해 중복 기록을 방지합니다.

## 서비스 간 독립성

Payment, Ledger, Settlement는 각각 독립된 서버와 DB를 사용합니다.

원장은 다른 서비스의 DB에 직접 접근하지 않고 HTTP API를 통해 필요한 데이터를 교환합니다.