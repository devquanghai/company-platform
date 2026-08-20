# Native JobRunr configuration

Project không định nghĩa bất kỳ `@ConfigurationProperties` nào cho scheduling.

Các property được dùng trực tiếp từ JobRunr 8.8.1:

```properties
jobrunr.database.skip-create
jobrunr.database.table-prefix
jobrunr.database.datasource
jobrunr.database.type
jobrunr.jobs.default-number-of-retries
jobrunr.jobs.retry-back-off-time-seed
jobrunr.jobs.delete-succeeded-jobs-after
jobrunr.jobs.permanently-delete-deleted-jobs-after
jobrunr.jobs.metrics.enabled
jobrunr.job-scheduler.enabled
jobrunr.background-job-server.enabled
jobrunr.background-job-server.worker-count
jobrunr.background-job-server.poll-interval-in-seconds
jobrunr.background-job-server.interrupt-jobs-await-duration-on-stop
jobrunr.background-job-server.metrics.enabled
jobrunr.dashboard.enabled
jobrunr.dashboard.port
jobrunr.dashboard.username
jobrunr.dashboard.password
jobrunr.miscellaneous.allow-anonymous-data-usage
```

Retention dùng namespace `jobrunr.jobs.*`; không dùng deprecated `jobrunr.background-job-server.delete-succeeded-jobs-after` và `permanently-delete-deleted-jobs-after`.
