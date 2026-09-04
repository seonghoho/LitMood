import type { RecordResponse, RecordStatus, Visibility } from './types'

/** 수정 다이얼로그가 들고 있는 편집 중인 값. */
export interface RecordDraft {
  status: RecordStatus
  rating: number | null
  moods: string[]
  review: string
  visibility: Visibility
}

/** 서버가 받는 부분 수정 페이로드. 넣지 않은 필드는 변경되지 않는다. */
export interface RecordPatch {
  status?: RecordStatus
  rating?: number
  clearRating?: true
  moods?: string[]
  review?: string
  visibility?: Visibility
}

const sameMoods = (a: string[], b: string[]) =>
  a.length === b.length && [...a].sort().join(' ') === [...b].sort().join(' ')

/**
 * 저장된 값과 달라진 것만 담는다 (이슈 #5).
 *
 * 서버는 넣지 않은 필드를 "변경 없음"으로 읽으므로, 지우려면 명시해야 한다 —
 * 리뷰는 빈 문자열, 별점은 clearRating. 반대로 바뀌지 않은 값까지 실어 보내면
 * 다른 탭에서 방금 고친 값을 되돌려 덮어쓴다.
 */
export function buildRecordPatch(record: RecordResponse, draft: RecordDraft): RecordPatch {
  const patch: RecordPatch = {}

  if (draft.status !== record.status) patch.status = draft.status

  if (draft.rating !== record.rating) {
    if (draft.rating !== null) {
      patch.rating = draft.rating
    } else if (draft.status === record.status) {
      patch.clearRating = true
    }
    // 상태를 함께 바꾼 경우엔 서버가 불변식대로 별점을 지우므로 따로 보내지 않는다
  }

  const savedMoods = record.moods.map((mood) => mood.displayName)
  if (!sameMoods(savedMoods, draft.moods)) patch.moods = draft.moods

  const review = draft.review.trim()
  if (review !== (record.review ?? '')) patch.review = review

  if (draft.visibility !== record.visibility) patch.visibility = draft.visibility

  return patch
}
