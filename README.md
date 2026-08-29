# Zendo

Zendo is an enterprise-grade B2B2C multi-vendor e-commerce marketplace. It is deliberately implemented as a **Domain-Driven Design (DDD) Modular Monolith** rather than prematurely decomposed into microservices. 

The primary engineering objective of this project is to demonstrate high cohesion, low coupling, explicit bounded contexts, transactional correctness, and concurrency safety. By leveraging a modular monolith approach, Zendo achieves easier transactional consistency and lower operational complexity than microservices while keeping future microservice extraction straightforward.

## Overview
Zendo manages the entire lifecycle of multi-vendor commerce: from catalog management and vendor ownership to flash sales, inventory reservation, order splitting, and distributed messaging.

## Key Features
- **Multi-vendor marketplace:** Vendor ownership and product lifecycle management.
- **Catalog & Inventory:** Strict inventory reservation ensuring `available = onHand - reserved`.
- **Flash Sale:** Highly concurrent, robust flash sales implementation.
- **Cart & Checkout:** Order splitting for multi-vendor carts.
- **Identity & Security:** JWT Bearer Authentication with distinct CUSTOMER, VENDOR, and ADMIN roles.
- **Notifications & Outbox Messaging:** Idempotent, at-least-once message processing using PostgreSQL Outbox and RabbitMQ.
- **Observability:** Centralized metrics, distributed tracing, and structured logging.
- **Resilience:** Bounded RabbitMQ retries, poison-message rejection, and graceful degradation.
- **Production Configuration:** Multi-environment Spring profiles with Docker containerization and resource limits.

## Architecture

Zendo relies on **Hexagonal Architecture (Ports and Adapters)** combined with **Dependency Inversion** to keep the core domain agnostic of infrastructure.

```text
API Layer (REST Controllers)
       ↓
Application Layer (Use Cases)
       ↓
Domain Layer (Aggregates & Entities)
       ↑
Infrastructure Layer (Adapters, JPA, Repositories)
```

## Bounded Contexts

| Context | Responsibility |
|---------|----------------|
| **Identity** | User identity and lifecycle |
| **Security** | Authentication, credentials, JWT, roles |
| **Vendor** | Vendor ownership and management |
| **Catalog** | Products and product information |
| **Inventory** | Stock and physical reservation |
| **Cart** | Shopping cart lifecycle |
| **Order** | Orders, checkout workflows, and multi-vendor splitting |
| **Payment** | Payment lifecycle |
| **Promotion** | Promotions and Flash Sale aggregate |
| **Pricing** | Pricing structures (Placeholder / Planned) |
| **Review** | Product reviews and ratings |
| **Notification** | Notification ledger and event handling |

## Core Engineering Decisions

### Inventory Consistency
PostgreSQL is the absolute source of truth for inventory. Concurrency-sensitive inventory operations rely on atomic database updates rather than in-memory validation. The core invariant maintained is:
`available = onHand - reserved` (where `onHand >= reserved >= 0`).

### Flash Sale & Concurrency
Flash Sales undergo extreme concurrency scenarios without overselling. Tested successfully with up to 10,000 concurrent HTTP clients contending for 100 allocated units, resulting in exactly 100 successful purchases and zero oversold items. This is achieved via atomic allocation, idempotency keys, and explicit PostgreSQL constraints.

### Event-Driven Architecture
Workflows spanning multiple bounded contexts rely on the **Transactional Outbox Pattern**:
1. Business transaction and Outbox event are committed atomically to PostgreSQL.
2. An Outbox relay publishes the message to RabbitMQ.
3. Dedicated consumer queues process messages with at-least-once delivery guarantees.
4. Consumers are designed to be strictly idempotent.

### Order Architecture
Zendo supports multi-vendor order splitting. A single checkout produces a `ParentOrder` (Aggregate Root) which manages multiple `ChildOrder` entities (split by vendor).

## Tech Stack

| Layer | Technology |
|---|---|
| **Language** | Java 21 |
| **Framework** | Spring Boot 3.5.x |
| **Database** | PostgreSQL 16 |
| **Cache / Key-Value** | Redis 7 |
| **Messaging** | RabbitMQ 3 |
| **Migration** | Flyway |
| **Testing** | JUnit 5, Testcontainers, Mockito |
| **Architecture Enforcement**| ArchUnit |
| **Observability** | Micrometer, Prometheus, OpenTelemetry Tracing |
| **Containerization** | Docker, Docker Compose |

## Project Structure

```text
Zendo/
├── src/
│   ├── main/
│   │   ├── java/com/zendo/
│   │   └── resources/
│   └── test/
├── Dockerfile            # Multi-stage production build
├── docker-compose.yml    # Local dependencies (Postgres, Redis, RabbitMQ)
├── docker-compose.prod.yml
├── pom.xml
├── mvnw
└── .env.example
```

## Getting Started

### Prerequisites
- JDK 21
- Docker & Docker Compose

### Local Infrastructure
Start the required local infrastructure (PostgreSQL, Redis, RabbitMQ):
```bash
docker-compose up -d
```

### Configuration
Copy the environment template and adjust if necessary (never commit actual secrets):
```bash
cp .env.example .env
```

### Running the Application
Use the Maven Wrapper to run the application locally:
```bash
# On Linux/macOS
./mvnw spring-boot:run

# On Windows
./mvnw.cmd spring-boot:run
```

### Running Tests
The project features a comprehensive test suite including unit tests, Testcontainer-based integration tests, ArchUnit governance rules, and concurrency validation.
```bash
# On Linux/macOS
./mvnw clean test

# On Windows
./mvnw.cmd clean test
```

## Docker

The application can be built into a non-root production-ready image:
```bash
docker build -t zendo-app:latest .
```
Production deployment topology is provided in `docker-compose.prod.yml`.

## Known Limitations
- A real external payment gateway is not yet integrated.
- Pricing module remains a structural placeholder.
- Formal RabbitMQ DLQ/DLX topologies for operational failure recovery are not yet finalized.
- Database backups and infrastructure provisioning are out-of-scope for the application codebase.