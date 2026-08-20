# JobRunr OSS vs Pro

Project này chỉ dùng API/feature phù hợp OSS cho core flow.

## OSS dùng trong project

- Persistent background jobs.
- Distributed processing qua shared StorageProvider.
- `JobScheduler` / `JobRequestScheduler`.
- `JobRequest` / `JobRequestHandler`.
- `@Recurring` basic CRON/interval.
- Retry/backoff.
- Dashboard basic.
- Micrometer/Actuator integration khi dependencies có mặt.
- Recurring job mặc định không overlap cùng recurring execution.

## Một số capability nâng cao thuộc Pro

- Transaction plugin tích hợp JobRunr job creation với Spring transaction.
- Catch-up recurring executions bị missed khi toàn cluster downtime.
- Advanced recurring scheduling/concurrency controls.
- Dynamic/priority queues.
- Advanced observability/search/dashboard capabilities.

Không tự clone các Pro feature trong `platform-schedule`.
