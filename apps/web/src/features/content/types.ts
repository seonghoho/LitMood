/**
 * 백엔드가 정규화해 내려주는 콘텐츠 모델 (ADR-010).
 *
 * M1 시점에는 손으로 정의한다. orval 코드젠(ADR-008)이 붙으면
 * `@litmood/api-client` 의 생성 타입으로 교체하고 이 파일은 삭제한다.
 */
export type ContentType = 'BOOK' | 'MOVIE' | 'MUSIC'
export type ProviderType = 'NAVER_BOOK' | 'TMDB' | 'SPOTIFY'

export interface ContentSummary {
  type: ContentType
  provider: ProviderType
  externalId: string
  title: string
  creators: string[]
  releasedOn: string | null
  coverUrl: string | null
  description: string | null
  metadata: Record<string, unknown>
}

export interface SearchResponse {
  results: Partial<Record<ContentType, ContentSummary[]>>
  failedProviders: ProviderType[]
  cached: boolean
}

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
