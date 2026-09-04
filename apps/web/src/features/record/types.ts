import type { RecordStatus, Visibility } from '@litmood/api-client'

/**
 * 기록 모델의 정의는 백엔드 DTO 에 있고, OpenAPI 를 거쳐 생성된다 (ADR-008).
 * 여기서는 생성 타입을 다시 내보내고 화면에서만 쓰는 라벨·판정을 함께 둔다.
 * 타입을 손으로 고치지 마세요 — 백엔드를 고치고 코드젠을 다시 돌립니다.
 */
export type {
  RecordStatus,
  Visibility,
  MoodTag,
  ContentRef,
  RecordResponse,
  RecordPage,
} from '@litmood/api-client'

export const STATUS_LABEL: Record<RecordStatus, string> = {
  WANT: '보고싶다',
  DOING: '보는중',
  DONE: '다봄',
  DROPPED: '중단',
}

/** 별점을 남길 수 있는 상태인지 — 백엔드 RecordStatus.allowsRating() 과 같은 규칙 (불변식 2). */
export function allowsRating(status: RecordStatus): boolean {
  return status !== 'WANT'
}

export const VISIBILITY_LABEL: Record<Visibility, string> = {
  PUBLIC: '전체 공개',
  FOLLOWERS: '팔로워만',
  PRIVATE: '나만 보기',
}
