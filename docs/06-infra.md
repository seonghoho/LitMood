# 06. 인프라 · 개발 환경 · 운영

## 로컬 개발 환경

```bash
# 1. 인프라 기동 (Postgres, Redis, MinIO)
docker compose -f infra/docker/compose.yml up -d

# 2. 백엔드
cd apps/api && ./gradlew bootRun --args='--spring.profiles.active=local'

# 3. 프론트엔드
pnpm install && pnpm --filter web dev

# 검증
curl http://localhost:8080/api/v1/ping     # {"service":"litmood-api","status":"ok",...}
open http://localhost:3000/health          # RSC 가 백엔드를 직접 호출하는 배선 확인
```

| 서비스      | 로컬 포트         | 비고                           |
| ----------- | ----------------- | ------------------------------ |
| Next.js     | 3000              |                                |
| Spring Boot | 8080              | Swagger UI: `/swagger-ui.html` |
| PostgreSQL  | 5432              |                                |
| Redis       | 6379              |                                |
| MinIO       | 9000 / 9001(콘솔) |                                |

## 시크릿 관리

**원칙: 시크릿은 어떤 형태로도 저장소에 커밋하지 않는다.**

| 환경 | 방식                                                                       |
| ---- | -------------------------------------------------------------------------- |
| 로컬 | `apps/api/.env.local` (gitignore). `.env.example`만 커밋                   |
| CI   | GitHub Actions Secrets                                                     |
| 운영 | Kubernetes Secret (`kubectl create secret` 또는 External Secrets Operator) |

**`.env.example`**

```env
# Database
DB_URL=jdbc:postgresql://localhost:5432/litmood
DB_USERNAME=litmood
DB_PASSWORD=

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# JWT
JWT_SECRET=                      # 최소 32바이트 랜덤
JWT_ACCESS_TTL=900               # 15분
JWT_REFRESH_TTL=1209600          # 14일

# 외부 Content Provider
NAVER_CLIENT_ID=
NAVER_CLIENT_SECRET=
TMDB_API_KEY=
SPOTIFY_CLIENT_ID=
SPOTIFY_CLIENT_SECRET=

# OAuth2
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
KAKAO_CLIENT_ID=
KAKAO_CLIENT_SECRET=

# Storage
S3_ENDPOINT=http://localhost:9000
S3_BUCKET=litmood
S3_ACCESS_KEY=
S3_SECRET_KEY=
```

### ⚠️ 즉시 조치 필요 — 기존 키 유출

`src/stores/book.store.js`에 네이버 API `clientId` / `clientSecret`이 평문으로 커밋되어
`origin/master`에 푸시된 상태다. 코드에서 제거해도 **git 히스토리에는 남는다.**

**조치 순서**

1. 네이버 개발자센터에서 해당 애플리케이션의 **Client Secret 재발급** (필수 — 유출된 키는 폐기)
2. 신규 키는 백엔드 환경변수로만 주입
3. (선택) 히스토리 정리 — 개인 저장소이고 협업자가 없다면 실익이 크지 않다. 1번이 본질적 해결책이다

## 컨테이너

**`apps/api/Dockerfile`** — 멀티스테이지 + 레이어드 JAR

```dockerfile
FROM gradle:8.10-jdk21 AS build
WORKDIR /app
COPY --chown=gradle:gradle . .
RUN gradle bootJar --no-daemon
RUN java -Djarmode=layertools -jar build/libs/*.jar extract --destination extracted

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# 변경 빈도가 낮은 레이어부터 복사 → 이미지 캐시 적중률 극대화
COPY --from=build /app/extracted/dependencies/ ./
COPY --from=build /app/extracted/spring-boot-loader/ ./
COPY --from=build /app/extracted/snapshot-dependencies/ ./
COPY --from=build /app/extracted/application/ ./
RUN addgroup -S app && adduser -S app -G app
USER app
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","org.springframework.boot.loader.launch.JarLauncher"]
```

**`apps/web/Dockerfile`** — Next standalone 출력 사용 (`output: 'standalone'`)
`node_modules` 전체 대신 실제 사용 모듈만 포함 → 이미지 크기 약 1/5.

**공통 원칙**

- 두 이미지 모두 **non-root 사용자**로 실행
- 이미지 태그는 `latest` 금지, git SHA 사용 → 롤백 가능성 확보

## Kubernetes (Kustomize)

```
infra/k8s/
├─ base/
│  ├─ api-{deployment,service,hpa}.yaml
│  ├─ web-{deployment,service}.yaml
│  ├─ postgres-statefulset.yaml     # 운영은 관리형 DB(RDS 등) 권장
│  ├─ redis-deployment.yaml
│  ├─ ingress.yaml
│  └─ kustomization.yaml
└─ overlays/
   ├─ dev/    # replicas 1, 리소스 축소, HPA 비활성
   └─ prod/   # replicas 3, HPA 3~10, PDB, 리소스 상향
```

**핵심 설정**

- **Probe 분리**: `readinessProbe` → `/actuator/health/readiness`, `livenessProbe` → `/actuator/health/liveness`.
  DB 커넥션 실패 시 트래픽만 차단되고 파드가 무한 재시작되지 않는다.
- **HPA는 API에만**: 검색 트래픽 스파이크는 백엔드에서 발생한다(외부 API 대기). 웹은 고정 replica.
- **PDB**: prod에서 `minAvailable: 2` → 노드 드레인 중 무중단
- `terminationGracePeriodSeconds: 30` + Spring `server.shutdown=graceful` → 처리 중 요청 보존

## CI/CD (GitHub Actions)

| 워크플로            | 트리거                         | 내용                                                              |
| ------------------- | ------------------------------ | ----------------------------------------------------------------- |
| `ci-web.yml`        | `apps/web`, `packages/**` 변경 | pnpm install → typecheck → lint → vitest → build                  |
| `ci-api.yml`        | `apps/api` 변경                | gradle test (Testcontainers) → build → openapi.json 생성          |
| `openapi-check.yml` | PR                             | 생성된 `openapi.json`과 커밋본 diff → **breaking change PR 차단** |
| `e2e.yml`           | PR (main 대상)                 | compose 기동 → Playwright 시나리오                                |
| `deploy.yml`        | `main` push                    | 이미지 빌드/푸시(SHA 태그) → `kustomize edit set image` → apply   |

## 테스트 전략

| 레이어      | 도구                            | 대상                                            |
| ----------- | ------------------------------- | ----------------------------------------------- |
| 백엔드 단위 | JUnit 5 + AssertJ               | 도메인 규칙 (별점 제약, 무드 정규화, 중복 기록) |
| 백엔드 통합 | **Testcontainers** (PG + Redis) | Repository 쿼리, 트랜잭션 경계                  |
| 외부 API    | **WireMock**                    | provider 응답·타임아웃·부분 실패 시나리오       |
| 프론트 단위 | Vitest + Testing Library        | 폼 검증, 훅                                     |
| E2E         | Playwright                      | 핵심 경로: 가입 → 검색 → 기록 → 컬렉션 공유     |

**H2 대신 Testcontainers를 쓰는 이유**: `jsonb`, `text[]`, 부분 유니크 인덱스는 H2에서 재현되지 않는다.
도메인 모델이 PG 고유 기능에 의존하므로(ADR-004) 테스트도 실제 PG여야 한다.

## 관측성

- **로그**: Logback JSON 인코더. `traceId`, `userId`, `path` MDC 주입
- **메트릭**: Micrometer → Prometheus. 커스텀 메트릭 — provider별 호출 지연/실패율, 캐시 적중률
- **알람 기준**: 5xx 비율 > 1% (5분), 검색 p95 > 3s, provider 실패율 > 20%
