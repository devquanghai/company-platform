#!/usr/bin/env bash
set -euo pipefail

cat <<'EOF'
Run the same built application twice against the SAME database:

Terminal 1:
  SERVER_PORT=8081 JOBRUNR_DASHBOARD_PORT=8001 \
  mvn -pl platform-schedule-demo -am spring-boot:run

Terminal 2:
  SERVER_PORT=8082 JOBRUNR_DASHBOARD_PORT=8002 \
  mvn -pl platform-schedule-demo -am spring-boot:run

Then enqueue ONE job from either instance:
  curl -X POST http://localhost:8081/api/jobs/notifications/NOTI-001

Both instances are BackgroundJobServers, but one JobRunr job instance is claimed by one server.
EOF
