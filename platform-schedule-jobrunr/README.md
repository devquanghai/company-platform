# platform-schedule — JobRunr enterprise starter

Project mẫu cho `platform-schedule` sử dụng **JobRunr 8.8.1**, **Spring Boot 4.0.7** và **Java 25**.

## Mục tiêu

- Không tạo `platform.schedule.*` properties.
- Không ShedLock, Quartz, Redis lock, leader election hay custom DB lock.
- Toàn bộ scheduling/retry/worker/dashboard/retention dùng native JobRunr.
- Multi-pod dùng cùng một persistent `StorageProvider`/database.
- Business-critical job vẫn phải idempotent/re-entrant để an toàn khi retry/crash.
- `platform-schedule` là thin integration module, không viết lại JobRunr.

## Modules

```text
platform-schedule-project
├── platform-schedule       # reusable thin dependency module
└── platform-schedule-demo  # runnable Spring Boot example
```

## Baseline

```text
Java        25
Spring Boot 4.0.7
JobRunr     8.8.1
Maven       3.9+
PostgreSQL  16 (demo)
```

## Quick start

```bash
docker compose up -d postgres
mvn clean verify
mvn -pl platform-schedule-demo -am spring-boot:run
```

Dashboard local:

```text
http://localhost:8000/dashboard
```

Demo API:

```bash
curl -X POST http://localhost:8080/api/jobs/notifications/NOTI-001
curl -X POST 'http://localhost:8080/api/jobs/payments/PAY-001?delaySeconds=30'
curl -X POST 'http://localhost:8080/api/jobs/settlements?businessDate=2026-08-17'
curl -X POST 'http://localhost:8080/api/jobs/retry-demo/REQ-001?fail=true'
```

## Multi-pod

Mọi pod phải dùng cùng database:

```text
                  PostgreSQL
                 JobRunr tables
                       ▲
          ┌────────────┼────────────┐
          │            │            │
        pod-1        pod-2        pod-3
        JobRunr      JobRunr      JobRunr
          │            │            │
          └──── optimistic claim ───┘
                       │
                   one winner
                       │
                       ▼
                     Job
```

JobRunr là distributed background job processor. Một job instance được claim bởi một `BackgroundJobServer`. Không thêm distributed lock khác quanh JobRunr.

## Native configuration only

Application chỉ dùng:

```yaml
jobrunr:
  database:
  jobs:
  job-scheduler:
  background-job-server:
  dashboard:
  miscellaneous:
```

Không có:

```yaml
platform:
  schedule:
```

Xem `platform-schedule-demo/src/main/resources/application.yml` và `application-prod.yml`.

## Retry

Global retry:

```yaml
jobrunr:
  jobs:
    default-number-of-retries: 5
    retry-back-off-time-seed: 3
```

Per job:

```java
@Job(name = "Process payment", retries = 5)
```

Không `while`, `Thread.sleep`, Spring Retry hay custom retry engine trong handler. Exception phải propagate ra JobRunr.

## Recurring jobs

Recurring job dùng stable ID:

```java
@Recurring(id = "daily-settlement", cron = "0 1 * * *")
@Job(name = "Daily settlement", retries = 5)
public void run() { ... }
```

Không đưa hostname, pod name hoặc random UUID vào recurring ID.

JobRunr OSS mặc định không tạo concurrent execution mới của cùng recurring job khi occurrence trước vẫn đang ở trạng thái `SCHEDULED`, `ENQUEUED` hoặc `PROCESSING`. Không cần ShedLock.

## Important: cluster-safe != business exactly-once

Ví dụ pod gọi API/commit DB thành công nhưng chết trước khi JobRunr mark job succeeded. Job có thể được recovered/retried. Do đó nghiệp vụ phải idempotent.

Demo settlement áp dụng pattern:

```text
JobRunr retry
    ↓
SettlementUseCase
    ↓
DB unique business_date
    ↓
transaction: STARTED + business work + COMPLETED
```

Nếu transaction đã commit và JobRunr retry, use case thấy `COMPLETED` và return mà không làm nghiệp vụ lần hai.

## Production checklist

- [ ] Persistent shared SQL database.
- [ ] Tất cả worker pods dùng cùng JobRunr database/schema.
- [ ] `jobrunr.background-job-server.enabled=true` ở worker pods.
- [ ] Stable recurring IDs.
- [ ] Không `@Scheduled` cho JobRunr jobs.
- [ ] Không ShedLock/Quartz/custom distributed lock.
- [ ] Retry dùng JobRunr native.
- [ ] Handler không swallow exception.
- [ ] Critical jobs idempotent/re-entrant.
- [ ] Payload nhỏ, chỉ giữ ID/reference cần thiết.
- [ ] Dashboard secured hoặc disabled ở production.
- [ ] Metrics bật nếu service có Micrometer/Actuator.
- [ ] Anonymous usage disabled.
- [ ] Retention configured.
- [ ] Graceful shutdown configured.
- [ ] Database migration strategy được quyết định rõ.
- [ ] `terminationGracePeriodSeconds` lớn hơn JobRunr stop wait.

## OSS / Pro notes

Xem `docs/OSS-VS-PRO.md`.

## Validation note

Project được sinh theo API/property names trong JobRunr 8.8.1 official docs. Môi trường tạo artifact hiện không có JDK 25/Maven sẵn, vì vậy `mvn clean verify` cần chạy trên máy có JDK 25 + Maven 3.9+.
