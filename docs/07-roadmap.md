# 07. 로드맵

각 마일스톤은 **끝에 배포 가능한 결과물**이 나오도록 끊었다.

---

## M0. 정리 및 기반 구축

- [x] Three.js 실험 코드·에셋 전량 제거
- [x] 제품 명세 / 아키텍처 / 도메인 모델 / API 명세 문서화
- [x] 기존 Vue 앱 제거 → pnpm 워크스페이스 + Turborepo 모노레포로 전환
- [x] `infra/docker/compose.yml` (PG + Redis + MinIO)
- [x] Spring Boot 스켈레톤 (레이어 구조, Flyway V1·V2 마이그레이션, `/actuator/health`, Testcontainers 스모크 테스트)
- [x] Next.js 스켈레톤 (App Router, Panda CSS, 무드 디자인 토큰)
- [x] Dockerfile · K8s 매니페스트(dev/prod overlay) · GitHub Actions 워크플로
- [ ] ⚠️ 네이버 API 키 재발급 및 환경변수 전환

**완료 조건**: `docker compose up` + `pnpm dev` + `bootRun` 3개로 로컬 전체 기동, 헬스체크 통과

---

## M1. 인증 + 콘텐츠 검색

- [x] 이메일 가입/로그인, JWT 발급·회전 + 재사용 감지 (F-01-01·03)
- [x] `ContentProvider` 인터페이스 + Naver / TMDB / Spotify 구현체
- [x] 가상 스레드 기반 병렬 검색 + 부분 실패 처리 + Redis 캐싱
- [x] 검색 UI (통합 검색창 + 타입별 탭 + 부분 실패 안내)
- [x] springdoc → `openapi.yaml` 추출 (orval 코드젠 배선은 M2 에서 연결)
- [ ] OAuth2 소셜 로그인 (F-01-02) — Google / Kakao

**완료 조건**: 로그인 후 "노르웨이의 숲" 검색 시 책·영화·음악 결과가 1.5초 내 표시

---

## M2. 기록 (핵심 경로) 🎯

- [x] 콘텐츠 스냅샷 저장 로직
- [x] 기록 CRUD + 무드 태그 + 공개범위 (F-03 전체 P0)
- [x] 기록 작성 모달 (검색 결과에서 화면 전환 없이)
- [x] 내 타임라인 + 커서 페이지네이션 + 필터 (F-04-01~02)
- [x] 공개 프로필 `/@handle` (SSR + OG 메타 + JSON-LD)
- [x] 이메일 가입/로그인 화면, 세션 자동 복구

**완료 조건**: `docs/00-product-overview.md`의 Critical Path가 **30초 내** 완주됨. **여기까지가 최소 제품이다.**
→ 달성. 가입 → 검색 → 기록(모달) → 타임라인 → 공개 프로필까지 화면 전환 없이 연결됨을 브라우저에서 확인했다.

---

## M3. 컬렉션 + 공유

- [ ] 컬렉션 CRUD, 아이템 추가/정렬 (F-05)
- [ ] 공개 컬렉션 페이지 + `next/og` 동적 OG 이미지
- [ ] 무드별 탐색 `/moods/{name}` (F-07-01)

**완료 조건**: 컬렉션 링크를 카카오톡에 붙였을 때 커버·제목·아이템 수가 담긴 카드가 뜬다

---

## M4. 소셜

- [ ] 팔로우 / 팔로잉 피드 / 좋아요 (F-06-01~03)
- [ ] 신고·차단 (F-06-05)
- [ ] 인기 콘텐츠 랭킹 — Redis Sorted Set (F-07-02)

---

## M5. 운영 준비

- [ ] Dockerfile 최적화, K8s 매니페스트 (base + dev/prod overlay)
- [ ] GitHub Actions CI/CD 전체 파이프라인
- [ ] Playwright E2E (핵심 경로)
- [ ] Prometheus 메트릭 + 알람
- [ ] 접근성 감사 (NFR-05), Lighthouse SEO 90+ (NFR-06)

---

## 이후 (Backlog)

- 연간 결산 페이지 (F-04-04)
- 규칙 기반 추천 — 무드 co-occurrence (F-07-03)
- 댓글 (F-06-04), 마크다운 리뷰
- PWA 오프라인 기록 작성
- Goodreads / 왓챠 데이터 임포트
- ML 추천 — **데이터가 충분히 쌓인 뒤 재검토**
