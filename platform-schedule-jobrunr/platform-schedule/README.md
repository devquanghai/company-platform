# platform-schedule

Thin reusable module around the official JobRunr Spring Boot 4 starter.

## Intentionally not included

- No `PlatformScheduleProperties`.
- No `platform.schedule.*` namespace.
- No custom auto-configuration that recreates JobRunr beans.
- No scheduler wrapper that delegates 1:1 to `JobScheduler`.
- No custom retry engine.
- No custom distributed lock.
- No ShedLock/Quartz/Redis lock.

Services may inject native JobRunr beans directly:

```java
private final JobRequestScheduler jobRequestScheduler;
```

Configuration is native `jobrunr.*` only.
