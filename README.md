# 📚 Driver Ledger System

EasyADJ 모빌리티 정산 플랫폼의 **원장 (Ledger) 서비스**입니다.

결제 (Payment) 서비스에서 발생한 거래를 **이중기입 원장 (Double-entry Ledger)** 방식으로 기록하고, 정산 (Settlement) 서비스가 기사별 미지급금과 결제 근거를 조회할 수 있도록
제공합니다.

원장 서비스는 결제와 정산 사이에서 금액의 정합성을 보장하고, 중복 기록이나 정산 불일치와 같은 금융 데이터 오류를 방지하는 것을 목표로 합니다.

---

## 🎯 주요 역할

### 1. 이중기입 분개 기록

하나의 거래를 `DEBIT`과 `CREDIT`으로 기록합니다.

하나의 요청에서 차변과 대변의 합계가 일치하는지 검증하여 원장의 정합성을 유지합니다.

### 2. 멱등성 보장

`Idempotency-Key` 헤더를 기반으로 동일한 요청이 네트워크 재시도 등으로 여러 번 전달될 경우 중복 분개 생성을 방어합니다.

이미 처리된 `Idempotency-Key`가 다시 전달되면 새로운 분개를 생성하지 않습니다.

### 3. 기사별 미지급금 계산

기사에게 아직 지급되지 않은 금액을 원장 데이터를 기반으로 계산합니다.

기사의 미지급 잔액은 다음과 같이 계산합니다.

```text
미지급 잔액 = DRIVER CREDIT 합계 - DRIVER DEBIT 합계
```

정산 서비스는 원장 서비스를 통해 다음 정보를 조회할 수 있습니다.

- 미지급금이 존재하는 기사 목록
- 특정 기사의 총 미지급금
- 미지급금의 근거가 되는 결제 내역

### 4. 결제 취소 반영

결제가 취소되는 경우 기존 결제와 반대 방향의 `PAYMENT_CANCEL` 분개를 추가하여 미지급 잔액을 감소시킵니다.

기존 원장 데이터를 수정하거나 삭제하지 않고 새로운 역분개를 추가하여 변경 이력을 보존합니다.

### 5. 정산 지급 상쇄

기사에게 실제 정산금이 지급되면 `SETTLEMENT` 분개를 기록하여 지급된 금액만큼 미지급 잔액을 차감합니다.

`SETTLEMENT`은 특정 결제 한 건에 종속되는 분개가 아니므로 다음 규칙을 사용합니다.

```text
paymentId = null
```

이를 통해 결제 발생부터 취소 및 실제 정산 지급까지의 금액 변화를 원장 기록으로 관리합니다.

### 6. 금액 정밀도 보장

금액 계산에는 `BigDecimal`을 사용하여 부동소수점 연산으로 인한 오차를 방지합니다.

서비스 간 JSON 통신에서는 금액을 문자열 형태로 전달합니다.

```json
{
  "totalUnpaidAmount": "15000"
}
```

원장에 기록되는 금액은 **1원 단위의 정수만 허용**하며 소수 금액은 저장하지 않습니다.

### 7. 원장 정합성 검증

지정한 기간 동안 기록된 원장의 `DEBIT`과 `CREDIT` 합계를 비교하여 전체 원장 정합성을 검증할 수 있습니다.

정합성이 유지되는 경우 `200 OK`, 불일치가 발견되는 경우 `409 Conflict`를 반환합니다.

---

# 🛠 Tech Stack

| 구분                | 기술                       |
|---------------------|----------------------------|
| Language            | Java 21                    |
| Framework           | Spring Boot 4.1.0          |
| Web                 | Spring Web                 |
| ORM                 | Spring Data JPA            |
| Production Database | PostgreSQL / Supabase      |
| Test Database       | H2                         |
| Build               | Gradle Kotlin DSL          |
| Utility             | Lombok                     |
| Test                | JUnit 5 / Spring Boot Test |
| Deployment          | Railway                    |

---

# 📡 API

## Base URL

운영 환경:

```text
https://driver-ledger-system-production.up.railway.app
```

Base Path:

```text
/api/ledger
```

---

## 1. 분개 기록

```http
POST /api/ledger/entries
```

결제, 결제 취소, 정산 지급에 따른 분개를 원장에 기록합니다.

### Header

```http
Idempotency-Key: payment-1001
Content-Type: application/json
```

### PAYMENT Request

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

### SETTLEMENT Request

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

### 주요 검증 규칙

- `DEBIT` 합계와 `CREDIT` 합계가 일치해야 합니다.
- 금액은 1원 단위의 정수여야 합니다.
- 금액은 JSON String으로 전달합니다.
- `Idempotency-Key`를 기반으로 중복 요청을 방어합니다.
- `ownerType`이 `DRIVER`인 분개에 기사 ID를 기록합니다.
- `SETTLEMENT` 분개의 `paymentId`는 `null`을 사용합니다.

### Response

```http
201 Created
```

```json
{
  "ledgerId": 1
}
```

---

## 2. 미지급 기사 목록 조회

```http
GET /api/ledger/unpaid?date=2026-08-22
```

지정한 날짜를 기준으로 미지급금이 존재하는 기사 목록을 조회합니다.

정산 서비스에서 정산 대상 기사를 조회할 때 사용할 수 있습니다.

미지급 잔액이 **0원보다 큰 기사만** 결과에 포함됩니다.

### Response Example

```json
{
  "data": [
    {
      "driverId": 1,
      "lastApprovedAt": "2026-08-21T06:26:53Z",
      "totalUnpaidAmount": "15000"
    }
  ],
  "targetDate": "2026-08-22"
}
```

---

## 3. 기사별 미지급금 조회

```http
GET /api/ledger?driver_id=1
```

특정 기사의 현재 미지급금 총액과 그 근거가 되는 결제 내역을 조회합니다.

### Response Example

```json
{
  "driverId": 1,
  "totalUnpaidAmount": "15000",
  "paymentDetails": [
    {
      "paymentId": 1001,
      "amount": "15000",
      "approvedAt": "2026-08-21T06:26:53Z"
    }
  ]
}
```

---

## 4. 원장 정합성 검증

```http
GET /api/ledger/verify?from=2026-08-21&to=2026-08-22
```

지정한 기간의 원장 데이터에 대해 전체 `DEBIT`과 `CREDIT` 합계가 일치하는지 검증합니다.

### 정상

```http
200 OK
```

정상인 경우 Response Body는 없습니다.

### 정합성 불일치

```http
409 Conflict
```

### 잘못된 기간

`from`이 `to`보다 이후인 경우:

```http
400 Bad Request
```

---

# 🚨 오류 응답

API 오류는 공통 오류 응답 형식을 사용합니다.

```json
{
  "code": "INVALID_REQUEST",
  "message": "요청 본문 형식이 올바르지 않습니다.",
  "transactionId": null
}
```

잘못된 JSON 요청 본문은 서버 내부 오류가 아닌 다음 상태 코드로 처리합니다.

```http
400 Bad Request
```

처리되지 않은 서버 내부 오류는 다음 상태 코드로 반환합니다.

```http
500 Internal Server Error
```

---

# 💰 금액 데이터 규약

서비스 간 금액 데이터는 다음 규칙을 따릅니다.

### 1. 내부 계산

모든 금액 계산에는 `BigDecimal`을 사용합니다.

```java
BigDecimal
```

`double`, `float` 기반의 금액 계산은 사용하지 않습니다.

### 2. JSON 표현

서비스 간 JSON 통신에서는 금액을 **JSON Number가 아닌 JSON String**으로 전달합니다.

```json
{
  "amount": "15000"
}
```

### 3. 원 단위 규칙

원장에 저장되는 금액은 **1원 단위의 정수**여야 합니다.

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

수수료 또는 지급액 계산 과정에서 소수가 발생하는 경우 해당 서비스에서 합의된 반올림 규칙을 적용한 뒤 원장에 전달해야 합니다.

---

# 📒 원장 Entry Type

현재 원장에서는 다음 거래 유형을 사용합니다.

| Entry Type       | 설명                            |
|------------------|---------------------------------|
| `PAYMENT`        | 결제 발생에 따른 분개           |
| `PAYMENT_CANCEL` | 결제 취소에 따른 역분개         |
| `SETTLEMENT`     | 정산 지급 완료에 따른 상쇄 분개 |

`SETTLEMENT` 분개는 기사에게 실제 정산금이 지급된 이후 기존 미지급 잔액을 차감하는 데 사용합니다.

```text
SETTLEMENT.paymentId = null
```

---

# ⚙️ 실행 방법

## 요구 환경

```text
Java 21
PostgreSQL
```

---

## Spring Profile

기본 Profile은 `local`입니다.

```properties
SPRING_PROFILES_ACTIVE=local
```

운영 환경에서는 반드시 `prod` Profile을 명시합니다.

```properties
SPRING_PROFILES_ACTIVE=prod
```

Railway 운영 환경에서도 `prod` Profile을 사용합니다.

---

## 환경 변수

운영 환경에서는 DB 접속정보를 코드나 README에 직접 기록하지 않고 환경 변수로 주입합니다.

```properties
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL=<PostgreSQL JDBC URL>
SPRING_DATASOURCE_USERNAME=<Database Username>
SPRING_DATASOURCE_PASSWORD=<Database Password>
```

`application-prod.properties`는 위 환경 변수를 사용하여 PostgreSQL에 연결합니다.

> 실제 DB 비밀번호 및 접속 자격 증명은 저장소에 커밋하지 않습니다.

---

## 실행

### Linux / macOS

```bash
./gradlew bootRun
```

### Windows

```powershell
.\gradlew.bat bootRun
```

---

# 🧪 테스트

## 전체 테스트

### Linux / macOS

```bash
./gradlew clean test
```

### Windows

```powershell
.\gradlew.bat clean test
```

## Build

### Linux / macOS

```bash
./gradlew clean bootJar
```

### Windows

```powershell
.\gradlew.bat clean bootJar
```

---

# ✅ 운영 환경 검증

Railway + Supabase PostgreSQL 환경에서 다음 흐름을 검증했습니다.

```text
PAYMENT 15,000
        ↓
기사 미지급 잔액 15,000

PAYMENT_CANCEL 5,000
        ↓
기사 미지급 잔액 10,000

SETTLEMENT 4,000
        ↓
기사 미지급 잔액 6,000
```

검증 항목:

- PAYMENT 분개 저장 및 조회
- 동일 `Idempotency-Key` 재요청 시 중복 분개 방지
- PAYMENT_CANCEL 역분개 반영
- SETTLEMENT 상쇄 분개 반영
- `SETTLEMENT.paymentId = null`
- 기사별 미지급 잔액 계산
- 미지급 기사 목록 조회
- 금액 JSON String 직렬화
- 원장 정합성 검증
- malformed JSON 요청 `400 Bad Request`
- Railway `prod` Profile 실행
- Supabase PostgreSQL 연결

---

# 📂 프로젝트 구조

```text
src
├── main
│   ├── java
│   │   └── com.example.driverledgersystem
│   │       ├── controller
│   │       ├── domain
│   │       ├── dto
│   │       ├── entity
│   │       ├── exception
│   │       ├── repository
│   │       └── service
│   │
│   └── resources
│       ├── application.properties
│       ├── application-local.properties
│       └── application-prod.properties
│
└── test
    └── java
```

---

# 🔐 원장 설계 원칙

Driver Ledger System은 다음 원칙을 기준으로 구현합니다.

- 모든 금액 계산은 `BigDecimal`을 사용한다.
- 원장 금액은 1원 단위의 정수로 기록한다.
- 서비스 간 금액 JSON 값은 문자열로 전달한다.
- 거래는 차변 (`DEBIT`)과 대변 (`CREDIT`)으로 기록한다.
- 하나의 거래에서 차변과 대변의 합계는 일치해야 한다.
- `Idempotency-Key`를 기반으로 중복 요청을 방어한다.
- 기사 미지급금은 원장의 `CREDIT - DEBIT`을 기준으로 계산한다.
- 결제 취소는 `PAYMENT_CANCEL` 역분개로 기록한다.
- 정산 지급은 `SETTLEMENT` 상쇄 분개로 기록한다.
- `SETTLEMENT`은 특정 결제에 종속되지 않으므로 `paymentId = null`을 사용한다.
- 기존 원장 기록을 수정하는 대신 새로운 분개를 추가하여 이력을 보존한다.
- 원장 데이터는 금융 데이터의 근거 (Source of Truth)로 취급한다.

---

# 🏗 System Context

```text
Payment Service
      │
      │ PAYMENT / PAYMENT_CANCEL
      │
      ▼
Driver Ledger System
      │
      │ 미지급 기사 / 잔액 / 결제 근거 조회
      │
      ▼
Settlement Service
      │
      │ 정산 지급
      │
      └──────────────► SETTLEMENT 상쇄 분개
                             │
                             ▼
                      Driver Ledger System
```

원장 서비스는 결제 발생부터 취소 및 정산 지급까지의 기록을 관리하며, 결제와 정산 사이의 **금융 데이터 정합성을 책임지는 중앙 원장 서비스**입니다.