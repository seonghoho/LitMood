import type { ContentType, ProviderType } from '@/features/content/types'

export type RecordStatus = 'WANT' | 'DOING' | 'DONE' | 'DROPPED'
export type Visibility = 'PUBLIC' | 'FOLLOWERS' | 'PRIVATE'

export interface MoodTag {
  name: string
  displayName: string
  color: string | null
  curated: boolean
}

export interface ContentRef {
  id: number
  type: ContentType
  provider: ProviderType
  externalId: string
  title: string
  creators: string[]
  releasedOn: string | null
  coverUrl: string | null
}

export interface RecordResponse {
  id: number
  status: RecordStatus
  rating: number | null
  moods: MoodTag[]
  review: string | null
  isSpoiler: boolean
  visibility: Visibility
  contextNote: string | null
  startedAt: string | null
  finishedAt: string | null
  repeatCount: number
  content: ContentRef
  createdAt: string
  updatedAt: string
}

export interface RecordPage {
  items: RecordResponse[]
  nextCursor: string | null
  totalCount: number
}

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
