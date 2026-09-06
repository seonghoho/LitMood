# 09. M5 — 운영 준비 (상세 명세)

> 남은 마지막 마일스톤. 이 문서만 보고 작업할 수 있도록 상세히 적었습니다.
> 진행 상황은 각 체크박스로 추적하세요.

**목표**: 실제로 배포하고, 배포한 것이 살아 있는지 알 수 있는 상태.

---

## M5-1. 컨테이너 이미지 실검증

현재 Dockerfile 2개는 **작성만 되어 있고 한 번도 빌드된 적이 없습니다.**

- [ ] `docker build -f apps/api/Dockerfile -t litmood-api apps/api` 성공 확인
- [ ] `docker build -f apps/web/Dockerfile -t litmood-web .` 성공 확인 (컨텍스트는 **저장소 루트**)
- [ ] 두 이미지를 compose 로 함께 띄워 `/health` 가 통과하는지 확인
- [ ] 이미지 크기 측정 — API 는 layered JAR, web 은 standalone 이므로 각각 300MB / 200MB 내외를 기대

### 예상되는 문제

**web 이미지**: `pnpm install --frozen-lockfile --ignore-scripts` 로 deps 를 만드는데,
Panda 코드젠(`prepare` 스크립트)이 건너뛰어집니다. build 단계에서 `pnpm --filter web build` 가
`panda codegen` 을 포함하므로 괜찮아야 하지만, 워크스페이스 링크가 깨지면 실패할 수 있습니다.

**api 이미지**: `gradle dependencies` 사전 실행이 실패해도 무시(`|| true`)하도록 해 뒀습니다.
캐시 최적화가 목적이라 실패해도 빌드는 진행됩니다.

---

## M5-2. Kubernetes 실배포

매니페스트는 `kubectl kustomize` 로 **문법만** 검증됐습니다.

- [ ] 로컬 클러스터 준비 (kind 또는 minikube, Docker Desktop 의 K8s 도 가능)
- [ ] `kubectl create namespace litmood-dev`
- [ ] 시크릿 생성 — **매니페스트에 넣지 않습니다**
      `bash
kubectl -n litmood-dev create secret generic litmood-api-secrets \
--from-env-file=apps/api/.env.local
`
- [ ] `kubectl apply -k infra/k8s/overlays/dev`
- [ ] 파드가 Running 이 되고 readiness probe 를 통과하는지 확인
- [ ] Ingress 로 접근 (로컬은 `/etc/hosts` 에 `litmood.local` 추가)

시크릿 생성 명령:

```bash
kubectl -n litmood-dev create secret generic litmood-api-secrets \
  --from-env-file=apps/api/.env.local
```

### 아직 결정되지 않은 것

**데이터베이스를 어디에 둘 것인가.** 현재 `base/` 에는 postgres StatefulSet 이 **없습니다** —
`docs/06-infra.md` 에 "운영은 관리형 DB 권장"이라고만 적혀 있습니다. 선택지:

| 방식            | 장점                  | 단점                           |
| --------------- | --------------------- | ------------------------------ |
| 관리형 (RDS 등) | 백업·failover 를 위임 | 비용, 로컬 클러스터에선 못 씀  |
| StatefulSet     | 클러스터 안에서 완결  | 백업·업그레이드를 직접 해야 함 |

로컬 검증만 하려면 `overlays/dev` 에 StatefulSet 을 추가하고, prod 는 관리형을 가리키게 하는
방식이 무난합니다.

- [ ] 위 결정을 내리고 `docs/06-infra.md` 에 반영

---

## M5-3. CI/CD 파이프라인 실행

워크플로 4개가 `.github/workflows/` 에 있지만 **한 번도 실행된 적이 없습니다.**

- [ ] 브랜치를 푸시해 `ci-web` / `ci-api` 가 초록이 되는지 확인
- [ ] `openapi-check` 검증 — 컨트롤러를 일부러 바꿔 스펙 drift 가 잡히는지 확인
- [ ] `k8s-validate` 가 dev/prod overlay 를 모두 빌드하는지 확인
- [ ] **아직 없는 워크플로 작성**:
  - [ ] `deploy.yml` — main 푸시 시 이미지 빌드/푸시(SHA 태그) → `kustomize edit set image` → apply
  - [ ] `e2e.yml` — Playwright (이슈 #9 와 함께)

### 예상되는 문제

`ci-api.yml` 은 Testcontainers 를 씁니다. GitHub Actions 의 `ubuntu-latest` 러너에는 Docker 가
기본 제공되므로 동작해야 하지만, 첫 실행은 이미지 pull 로 느립니다.

`openapi-check.yml` 의 breaking change 검사는 `oasdiff-action` 을 쓰는데,
base 브랜치에 `openapi.yaml` 이 없으면 실패합니다 — `continue-on-error: true` 로 두었습니다.

---

## M5-4. 관측성 (NFR-07)

- [ ] Prometheus 로 `/actuator/prometheus` 스크레이프 확인
      (deployment 에 스크레이프 annotation 은 이미 있음)
- [ ] **커스텀 메트릭 추가** — 지금은 기본 메트릭만 나옵니다
  - [ ] provider 별 호출 지연·실패율 (`ContentSearchService` 에 `@Timed` 또는 수동 계측)
  - [ ] 검색 캐시 적중률 (`SearchCache` 에 카운터)
  - [ ] 기록 생성 수 (제품 지표)
- [ ] 구조화 로그 — Logback JSON 인코더 + MDC(`traceId`, `userId`, `path`)
      **현재는 평문 로그입니다**
- [ ] 알람 규칙 (`docs/06-infra.md` 기준)
  - 5xx 비율 > 1% (5분)
  - 검색 p95 > 3s
  - provider 실패율 > 20%

---

## M5-5. 품질 게이트

### 접근성 (NFR-05)

- [ ] axe 또는 Lighthouse 접근성 감사
- [ ] 키보드만으로 핵심 경로 완주 (검색 → 기록 모달 → 저장)
- [ ] 명도 대비 4.5:1 확인 — **무드 색 위의 흰 텍스트가 특히 의심스럽습니다**
      (`위로 #C9A227`, `해방 #3E8E5A` 등 밝은 색)
- [ ] 다이얼로그 포커스 트랩 — 현재 Esc 로 닫기와 초기 포커스만 구현돼 있습니다

### SEO (NFR-06)

- [ ] Lighthouse SEO 90+ (공개 프로필, 컬렉션, 무드 페이지)
- [ ] `sitemap.ts` / `robots.ts` **미작성**
- [ ] 카카오톡·트위터에서 실제 공유 카드 확인 (배포 후)

### 성능 (NFR-01, NFR-02)

- [ ] 자체 DB 조회 API p95 < 300ms 측정 — **아직 측정된 적 없습니다**
- [ ] 검색 p95 < 1.5s 측정
- [ ] 부하 도구 선택 (k6 권장 — 스크립트가 JS 라 팀이 읽기 쉬움)

---

## M5-6. 운영 최소 요건

- [ ] 신고 처리 화면 + 관리자 권한 — [#28](https://github.com/seonghoho/LitMood/issues/28)
      신고를 **접수하는** 길은 [#18](https://github.com/seonghoho/LitMood/issues/18) 에서 만들었지만
      쌓인 것을 **볼 방법이 없습니다**. 현재 모든 사용자가 동일 권한(`ROLE_USER`)이라
      관리자를 어떻게 임명할지부터 정해야 합니다

  > **그때까지의 운영 절차.** 화면이 생기기 전에는 DB 를 직접 봅니다.
  >
  > ```bash
  > docker exec -it litmood-postgres psql -U litmood -d litmood -c "
  >   SELECT r.id, r.target_type, r.target_id, r.reason, r.detail, r.created_at
  >   FROM reports r WHERE r.status = 'PENDING' ORDER BY r.created_at;"
  > ```
  >
  > 대상은 `target_type` 에 따라 `records.id` / `collections.id` / `users.id` 입니다.
  > 처리했으면 상태를 옮깁니다 — 같은 사람이 같은 대상을 다시 신고해도 접수되지 않으므로,
  > `PENDING` 으로 두면 큐가 계속 쌓인 것처럼 보입니다.
  >
  > ```sql
  > UPDATE reports SET status = 'REVIEWED', resolved_at = now() WHERE id = :id;
  > ```

- [ ] Rate limit — `docs/05-api-spec.md` 에 정책만 있고 **구현이 없습니다**
      (인증 600/min, 비인증 60/min, 검색 30/min — Redis 토큰 버킷)
- [ ] 개인정보 처리방침 / 이용약관 페이지

---

## 완료 조건

- 로컬 K8s 클러스터에 배포되어 브라우저로 접근 가능
- CI 4종이 초록
- Prometheus 에서 provider 실패율을 볼 수 있음
- Lighthouse SEO 90+ / 접근성 위반 0건
- 핵심 경로 E2E 가 CI 에서 자동 실행됨
