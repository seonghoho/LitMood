export { apiFetcher, setAccessToken, ApiError } from './fetcher'
export type { ApiResult, ProblemDetail } from './fetcher'

/**
 * OpenAPI 에서 생성된 모델 타입 (ADR-008). 손으로 고치지 않는다 —
 * 백엔드 DTO 를 바꾼 뒤 `pnpm fetch-spec && pnpm codegen` 을 다시 돌린다.
 */
export * from './generated/model'

// 생성된 호출 함수는 배럴에서 재수출하지 않는다. operationId 가 컨트롤러
// 메서드명에서 나와 태그 간에 겹치고(create1 / get1 / update1), 아직 쓰는 곳도 없다.
// 필요해지면 태그 경로에서 직접 가져온다:
//   import { myTimeline } from '@litmood/api-client/src/generated/records/records'
