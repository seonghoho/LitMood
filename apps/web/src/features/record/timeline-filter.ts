import type { ContentType } from '@/features/content/types'
import type { RecordStatus } from './types'

/** 타임라인이 들고 있는 필터 상태 (F-04-02). */
export interface TimelineFilter {
  type: ContentType | null
  status: RecordStatus | null
  /**
   * 정규화된 무드 이름(`MoodTag.name`). 서버는 이 값으로 대조한다 —
   * 화면에 보이는 `displayName`("새벽")을 그대로 보내면 조용히 0건이 나온다.
   */
  moods: string[]
  /** 최소 별점. 0.5 단위 (F-03-03) */
  minRating: number | null
  /** 빈 문자열이면 "지정 안 함" — input[type=date] 가 비었을 때의 값이다 */
  from: string
  to: string
}

export const EMPTY_TIMELINE_FILTER: TimelineFilter = {
  type: null,
  status: null,
  moods: [],
  minRating: null,
  from: '',
  to: '',
}

/** 고를 수 있는 최소 별점. 서버가 0.5 단위만 허용한다. */
export const MIN_RATING_OPTIONS = [5, 4.5, 4, 3.5, 3, 2.5, 2, 1.5, 1, 0.5] as const

export function isFilterActive(filter: TimelineFilter): boolean {
  return (
    filter.type !== null ||
    filter.status !== null ||
    filter.moods.length > 0 ||
    filter.minRating !== null ||
    filter.from !== '' ||
    filter.to !== ''
  )
}

/** 무드는 다중 선택이다 — 여러 개를 고르면 서버가 "그중 하나라도" 로 본다. */
export function toggleMoodFilter(filter: TimelineFilter, name: string): TimelineFilter {
  const moods = filter.moods.includes(name)
    ? filter.moods.filter((m) => m !== name)
    : [...filter.moods, name]
  return { ...filter, moods }
}

/**
 * 서버가 받는 쿼리스트링으로 옮긴다 (`GET /api/v1/records/me`).
 *
 * 화면 상태와 쿼리를 분리해 둔 이유는 어긋나기 쉬운 지점이 셋이나 있기 때문이다 —
 * 무드는 정규화된 이름이어야 하고, 여러 값은 반복 파라미터여야 하며,
 * "지정 안 함"을 빈 문자열로 들고 있다가 그대로 보내면 서버가 파싱에 실패한다.
 */
export function buildTimelineQuery(filter: TimelineFilter, cursor: string | null): string {
  const params = new URLSearchParams()

  if (filter.type) params.set('types', filter.type)
  if (filter.status) params.set('status', filter.status)

  // 반복 파라미터로 보낸다 — Spring 의 List<String> 바인딩이 기대하는 형태다
  for (const mood of filter.moods) {
    params.append('moods', mood)
  }

  // BigDecimal 로 받으므로 3 이 아니라 "3.0" 으로 보낸다
  if (filter.minRating !== null) params.set('minRating', filter.minRating.toFixed(1))

  if (filter.from) params.set('from', filter.from)
  if (filter.to) params.set('to', filter.to)
  if (cursor) params.set('cursor', cursor)

  return params.toString()
}
