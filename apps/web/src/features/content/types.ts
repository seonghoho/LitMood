import type { ContentType, ProviderType } from '@litmood/api-client'

/**
 * 콘텐츠 모델의 정의는 백엔드 DTO 에 있고, OpenAPI 를 거쳐 생성된다 (ADR-008).
 * 여기서는 생성 타입을 다시 내보내고 화면에서만 쓰는 라벨을 함께 둔다.
 * 타입을 손으로 고치지 마세요 — 백엔드를 고치고 코드젠을 다시 돌립니다.
 */
export type { ContentType, ProviderType, ContentSummary, SearchResponse } from '@litmood/api-client'

export const CONTENT_TYPES: ContentType[] = ['BOOK', 'MOVIE', 'MUSIC']

export const CONTENT_TYPE_LABEL: Record<ContentType, string> = {
  BOOK: '책',
  MOVIE: '영화',
  MUSIC: '음악',
}

/** 부분 실패 안내에 provider 대신 사용자가 아는 말로 표시한다. */
export const PROVIDER_CONTENT_LABEL: Record<ProviderType, string> = {
  NAVER_BOOK: '책',
  TMDB: '영화',
  SPOTIFY: '음악',
}
