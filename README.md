# 📚 Driver Ledger System

EasyADJ 모빌리티 정산 플랫폼의 **원장(Ledger) 서비스**입니다.

결제(Payment) 서비스에서 발생한 거래를 **이중기입 원장(Double-entry Ledger)** 방식으로 기록하고, 정산(Settlement) 서비스가 기사별 미지급금과 결제 근거를 조회할 수 있도록 제공합니다.

원장 서비스는 결제와 정산 사이에서 금액의 정합성을 보장하고, 중복 기록이나 정산 불일치와 같은 금융 데이터 오류를 방지하는 것을 목표로 합니다.

---

## 🎯 주요 역할

### 1. 이중기입 분개 기록

하나의 거래를 `DEBIT`과 `CREDIT`으로 기록합니다.

차변과 대변의 합계가 일치하도록 검증하여 원장의 정합성을 유지합니다.

### 2. 멱등성 보장

`Idempotency-Key` 헤더를 이용하여 동일한 결제 요청이 네트워크 재시도 등으로 여러 번 전달되더라도 중복 분개가 생성되지 않도록 처리합니다.

### 3. 기사별 미지급금 계산

기사에게 아직 지급되지 않은 금액을 원장 데이터를 기반으로 계산합니다.

정산 서버는 원장 서비스를 통해 다음 정보를 조회할 수 있습니다.

* 미지급금이 존재하는 기사 목록
* 특정 기사의 총 미지급금
* 미지급금의 근거가 되는 결제 내역

### 4. 금액 정밀도 보장

금액 계산에는 `BigDecimal`을 사용합니다.

서비스 간 JSON 통신에서는 금액을 문자열 형태로 전달합니다.

```json
{
  "totalUnpaidAmount": "15000"
}
```

이를 통해 `double`, `float` 사용으로 인해 발생할 수 있는 부동소수점 오차를 방지합니다.

---

# 🛠 Tech Stack

| 구분            | 기술                         |
| ------------- | -------------------------- |
| Language      | Java 21                    |
| Framework     | Spring Boot 4.1.0          |
| Web           | Spring Web                 |
| ORM           | Spring Data JPA            |
| Database      | PostgreSQL                 |
| Test Database | H2                         |
| Build         | Gradle Kotlin DSL          |
| Utility       | Lombok                     |
| Test          | JUnit 5 / Spring Boot Test |

---

# 📡 API

Base Path

```text
/api/ledger
```

---

## 1. 분개 기록

```http
POST /api/ledger/entries
```

결제 서버에서 발생한 거래를 원장에 기록합니다.

### Header

```http
Idempotency-Key: payment-1001
```

### Request

```json
{
  "driverId": 1,
  "entryType": "PAYMENT",
  "entries": [
    {
      "direction": "DEBIT",
      "amount": "10000",
      "paymentId": 1001,
      "ownerType": "PLATFORM"
    },
    {
      "direction": "CREDIT",
      "amount": "10000",
      "paymentId": 1001,
      "ownerType": "DRIVER"
    }
  ]
}
```

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
GET /api/ledger/unpaid?date=2026-08-21
```

지정한 날짜를 기준으로 미지급금이 존재하는 기사 목록을 조회합니다.

정산 서비스에서 정산 대상 기사를 조회할 때 사용할 수 있습니다.

---

## 3. 기사별 미지급금 조회

```http
GET /api/ledger?driver_id=1
```

특정 기사의 미지급금 총액과 그 근거가 되는 결제 내역을 조회합니다.

### Response Example

```json
{
  "driverId": 1,
  "totalUnpaidAmount": "15000",
  "paymentDetails": [
    {
      "paymentId": 1001,
      "amount": "10000",
      "approvedAt": "2026-08-21T10:30:00Z"
    },
    {
      "paymentId": 1002,
      "amount": "5000",
      "approvedAt": "2026-08-21T11:00:00Z"
    }
  ]
}
```

---

# 💰 금액 데이터 규약

서비스 간 금액 데이터는 다음 규칙을 따릅니다.

### 내부 계산

```java
BigDecimal
```

### JSON

```json
"15000"
```

즉, JSON Number가 아닌 **JSON String**으로 전달합니다.

```json
{
  "amount": "15000"
}
```

이를 통해 서비스 간 금액 표현 방식의 차이와 부동소수점 오차를 방지합니다.

---

# ⚙️ 실행 방법

## 요구 환경

```text
Java 21
PostgreSQL
```

---

## 환경 변수

다음 환경 변수가 필요합니다.

```properties
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/ledger
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=password
```

기본 Spring Profile은 다음과 같습니다.

```properties
SPRING_PROFILES_ACTIVE=local
```

운영 환경에서는 다음과 같이 설정할 수 있습니다.

```properties
SPRING_PROFILES_ACTIVE=prod
```

---

## 실행

Linux / macOS

```bash
./gradlew bootRun
```

Windows

```powershell
.\gradlew.bat bootRun
```

---

# 🧪 테스트

전체 테스트 실행

Linux / macOS

```bash
./gradlew clean test
```

Windows

```powershell
.\gradlew.bat clean test
```

빌드

Linux / macOS

```bash
./gradlew clean bootJar
```

Windows

```powershell
.\gradlew.bat clean bootJar
```

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

* 모든 금액 계산은 `BigDecimal`을 사용한다.
* 서비스 간 금액 JSON 값은 문자열로 전달한다.
* 거래는 차변과 대변으로 기록한다.
* 동일 거래의 중복 기록을 허용하지 않는다.
* 원장을 기준으로 정산 가능한 금액을 계산한다.
* 원장 데이터는 금융 데이터의 근거(Source of Truth)로 취급한다.

---

# 🏗 System Context

```text
Payment Service
      │
      │ 결제 분개 기록
      ▼
Driver Ledger System
      │
      │ 미지급금 / 결제 근거 조회
      ▼
Settlement Service
```

원장 서비스는 결제와 정산 사이의 **금융 데이터 정합성을 책임지는 중앙 원장 서비스**입니다.
