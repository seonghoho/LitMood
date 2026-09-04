#!/usr/bin/env bash
# 백엔드를 로컬 인프라에 물려 기동한다.
#
#   ./scripts/dev-api.sh          실제 provider (apps/api/.env.local 의 키 사용)
#   ./scripts/dev-api.sh --stub   외부 provider 를 tools/stub-provider 로 대체
#
# 키 없이도 전체 스택을 굴려볼 수 있게 --stub 을 제공한다.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/apps/api"

# 1) .env.local 이 있으면 읽어 들인다 (없어도 기동은 된다)
if [ -f .env.local ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env.local
  set +a
fi

# 2) 로컬 인프라 포트 — infra/docker/.env 로 오버라이드했다면 그 값을 따른다
if [ -f "$ROOT/infra/docker/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  . "$ROOT/infra/docker/.env"
  set +a
fi

export DB_URL="${DB_URL:-jdbc:postgresql://localhost:${POSTGRES_PORT:-5432}/litmood}"
export DB_USERNAME="${DB_USERNAME:-litmood}"
export DB_PASSWORD="${DB_PASSWORD:-litmood_local}"
export REDIS_HOST="${REDIS_HOST:-localhost}"
export REDIS_PORT="${REDIS_PORT:-6379}"

# JWT_SECRET 이 없으면 기동 자체가 막힌다(의도된 동작).
# 로컬에서는 재시작마다 세션이 끊기지 않도록 고정값을 쓴다.
export JWT_SECRET="${JWT_SECRET:-litmood-local-development-secret-do-not-use-in-production}"

if [ "${1:-}" = "--stub" ]; then
  STUB="http://localhost:${STUB_PORT:-9876}"
  echo "→ 외부 provider 를 스텁($STUB)으로 대체합니다. 'pnpm stub' 이 떠 있어야 합니다."
  export NAVER_CLIENT_ID=stub NAVER_CLIENT_SECRET=stub
  export TMDB_API_KEY=stub SPOTIFY_CLIENT_ID=stub SPOTIFY_CLIENT_SECRET=stub
  export LITMOOD_PROVIDER_NAVER_BASEURL="$STUB"
  export LITMOOD_PROVIDER_TMDB_BASEURL="$STUB"
  export LITMOOD_PROVIDER_SPOTIFY_BASEURL="$STUB"
  export LITMOOD_PROVIDER_SPOTIFY_TOKENURL="$STUB/api/token"
fi

exec ./gradlew bootRun --args='--spring.profiles.active=local'
