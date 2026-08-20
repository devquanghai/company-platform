# Architecture

## Responsibility split

```text
JobRunr
  ├── persistence
  ├── scheduling
  ├── distributed claim
  ├── retry/backoff
  ├── job state
  ├── dashboard
  └── metrics

Application adapter
  ├── JobRequest
  ├── JobRequestHandler
  └── @Recurring trigger

Application/domain
  └── business use cases
```

`domain` và `application` không cần biết JobRunr. JobRunr nằm ở inbound scheduling adapter.

## Multi-instance

```text
                                  Shared SQL DB
                               JobRunr StorageProvider
                                       ▲
                                       │
                     ┌─────────────────┼─────────────────┐
                     │                 │                 │
                   pod-a             pod-b             pod-c
             BackgroundJobServer BackgroundJobServer BackgroundJobServer
                     │                 │                 │
                     └──────── optimistic claim ─────────┘
                                       │
                                   one server
                                       │
                                       ▼
                                   handler
```

Không thêm lock bên ngoài JobRunr để quyết định pod nào chạy job.

## Critical side effects

Cluster claim không thay thế idempotency ở business layer.

```text
Job PROCESSING
      ↓
remote API / DB commit
      ↓
pod crashes before JobRunr marks success
      ↓
recovery/retry
      ↓
business operation may be called again
```

API hoặc DB side-effect phải có idempotency/business unique key phù hợp.
