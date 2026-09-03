#!/usr/bin/env bash
# 개발 환경이 갖춰졌는지 점검한다. 새 PC 에서 처음 받았을 때 먼저 돌려 본다.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
ok=0; fail=0

check() {
  local label="$1"; shift
  if "$@" >/dev/null 2>&1; then printf '  ✅ %s\n' "$label"; ok=$((ok+1))
  else printf '  ❌ %s\n' "$label"; fail=$((fail+1)); fi
}

echo "== 툴체인 =="
check "Node 20 이상"    bash -c 'node -e "process.exit(parseInt(process.versions.node) >= 20 ? 0 : 1)"'
check "pnpm"            command -v pnpm
check "Java 21"         bash -c 'java -version 2>&1 | grep -q "\"21"'
check "Docker 데몬 기동" docker info

echo "== 인프라 =="
check "PostgreSQL 접속" bash -c 'docker exec litmood-postgres pg_isready -U litmood'
check "Redis 접속"      bash -c 'docker exec litmood-redis redis-cli ping'
check "MinIO 기동"      bash -c 'docker ps --format "{{.Names}}" | grep -q litmood-minio'

echo "== 설정 =="
check "루트 의존성 설치" test -d node_modules
check ".env.example 존재" test -f .env.example
if [ -f apps/api/.env.local ]; then
  echo "  ✅ apps/api/.env.local 존재"
  ok=$((ok+1))
else
  echo "  ⚠️  apps/api/.env.local 없음 — 'pnpm dev:stub' 으로는 동작합니다"
fi

echo
echo "통과 $ok / 실패 $fail"
[ "$fail" -eq 0 ] || {
  echo
  echo "다음을 확인하세요:"
  echo "  - Docker Desktop 이 켜져 있는가"
  echo "  - pnpm infra:up 을 실행했는가"
  echo "  - 포트 충돌 시 infra/docker/.env.example 을 .env 로 복사해 조정"
  exit 1
}
