import type { Visibility } from '@/features/record/types'
import type { CollectionResponse } from './types'

/** 메타 편집 폼이 들고 있는 값. */
export interface CollectionDraft {
  title: string
  /** 빈 문자열이면 "설명 없음" — textarea 가 비었을 때의 값이다 */
  description: string
  visibility: Visibility
}

/** 서버가 받는 부분 수정 페이로드. 넣지 않은 필드는 변경되지 않는다. */
export interface CollectionPatch {
  title?: string
  description?: string
  coverUrl?: string
  visibility?: Visibility
}

export function toDraft(collection: CollectionResponse): CollectionDraft {
  return {
    title: collection.title,
    description: collection.description ?? '',
    visibility: collection.visibility,
  }
}

/**
 * 저장된 값과 달라진 것만 담는다 (기록의 규칙과 같다).
 *
 * 바뀌지 않은 값까지 실어 보내면 다른 탭에서 방금 고친 값을 되돌려 덮어쓴다.
 * 반대로 설명을 지울 때는 빈 문자열을 <b>명시해야</b> 한다 — 서버는 null 을
 * "변경 없음"으로 읽는다.
 */
export function buildCollectionPatch(
  collection: CollectionResponse,
  draft: CollectionDraft,
): CollectionPatch {
  const patch: CollectionPatch = {}

  const title = draft.title.trim()
  // 제목은 비울 수 없다 — 공유 URL 의 이름이다. 빈 값은 보내지 않고 무시한다.
  if (title && title !== collection.title) patch.title = title

  const description = draft.description.trim()
  if (description !== (collection.description ?? '')) patch.description = description

  if (draft.visibility !== collection.visibility) patch.visibility = draft.visibility

  return patch
}

/**
 * 커버 지정 (#7).
 *
 * `null` 은 "자동" 이고, 서버에서는 빈 문자열이 그 뜻이다 — 지우면
 * `resolveCoverUrl` 이 다시 첫 아이템의 표지를 따라간다.
 */
export function buildCoverPatch(coverUrl: string | null): CollectionPatch {
  return { coverUrl: coverUrl ?? '' }
}

/** 지금 대표 이미지로 쓰이는 표지가 어느 아이템의 것인지 — 선택 상태 표시에 쓴다. */
export function selectedCoverUrl(collection: CollectionResponse): string | null {
  return collection.coverPinned ? collection.coverUrl : null
}
