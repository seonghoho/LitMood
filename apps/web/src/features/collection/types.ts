/**
 * 컬렉션 모델의 정의는 백엔드 DTO 에 있고, OpenAPI 를 거쳐 생성된다 (ADR-008).
 * 타입을 손으로 고치지 마세요 — 백엔드를 고치고 코드젠을 다시 돌립니다.
 *
 * `CollectionItem` 은 생성 이름이 `CollectionItemResponse` 다. 화면 쪽 호출부가
 * 이미 쓰던 이름을 유지하려고 여기서만 별칭을 준다.
 */
export type {
  CollectionItemResponse as CollectionItem,
  CollectionSummary,
  CollectionResponse,
} from '@litmood/api-client'
