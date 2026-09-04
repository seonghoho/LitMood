# 모노레포 루트를 빌드 컨텍스트로 사용한다:
#   docker build -f apps/web/Dockerfile -t litmood-web .

# ── deps ──────────────────────────────────────────────────────
FROM node:22-alpine AS deps
RUN corepack enable
WORKDIR /repo
COPY pnpm-workspace.yaml package.json pnpm-lock.yaml .npmrc ./
COPY apps/web/package.json apps/web/
COPY packages/ui/package.json packages/ui/
COPY packages/api-client/package.json packages/api-client/
COPY packages/config/package.json packages/config/
# --ignore-scripts: 이 단계에서는 panda codegen 이 필요 없다 (소스가 아직 없음)
RUN pnpm install --frozen-lockfile --ignore-scripts

# ── build ─────────────────────────────────────────────────────
FROM node:22-alpine AS build
RUN corepack enable
WORKDIR /repo
COPY --from=deps /repo/node_modules ./node_modules
COPY --from=deps /repo/apps/web/node_modules ./apps/web/node_modules
COPY --from=deps /repo/packages ./packages
COPY . .
ENV NEXT_TELEMETRY_DISABLED=1
RUN pnpm --filter web build

# ── runtime ───────────────────────────────────────────────────
FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 PORT=3000

RUN addgroup -S nodejs -g 1001 && adduser -S nextjs -u 1001 -G nodejs

# output: 'standalone' — node_modules 전체가 아닌 실제 사용 모듈만 포함된다
COPY --from=build --chown=nextjs:nodejs /repo/apps/web/.next/standalone ./
COPY --from=build --chown=nextjs:nodejs /repo/apps/web/.next/static ./apps/web/.next/static
COPY --from=build --chown=nextjs:nodejs /repo/apps/web/public ./apps/web/public

USER nextjs
EXPOSE 3000
CMD ["node", "apps/web/server.js"]
