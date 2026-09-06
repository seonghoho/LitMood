import type { ProviderType } from '@/features/content/types'
import type { RecordResponse } from './types'

/** 콘텐츠를 가리키는 좌표. provider 안에서만 externalId 가 유일하므로 둘을 함께 쓴다. */
export interface ContentRefKey {
  provider: ProviderType
  externalId: string
}

/**
 * 서버가 `refs` 로 받는 형식과 같다 (`PROVIDER:externalId`).
 * 응답의 콘텐츠에서도 같은 방식으로 만들 수 있어 요청과 응답을 키로 맞출 수 있다.
 */
export function contentKey(content: ContentRefKey): string {
  return `${content.provider}:${content.externalId}`
}

/** 서버가 한 번에 받는 상한과 같은 값. 넘기면 400 이다. */
export const MAX_REFS_PER_REQUEST = 50

/**
 * 검색 결과 중 내가 기록한 것을 찾는 조회 경로 (#11).
 *
 * 검색 응답에 함께 실을 수 없는 정보다 — 검색 결과는 서버가 캐시하므로
 * 사용자별 값을 섞으면 다른 사람에게 샌다. 그래서 화면이 따로 묻는다.
 *
 * 볼 것이 없으면 null 을 돌려준다. 빈 요청을 보내면 왕복만 낭비한다.
 */
export function buildByContentPath(contents: ContentRefKey[]): string | null {
  if (contents.length === 0) return null

  const params = new URLSearchParams()
  // 화면에 보이는 것만 묻는다. 상한을 넘으면 서버가 통째로 거절하므로 앞에서 자른다.
  for (const content of contents.slice(0, MAX_REFS_PER_REQUEST)) {
    params.append('refs', contentKey(content))
  }
  return `/api/v1/records/me/by-content?${params.toString()}`
}

/**
 * 조회 결과를 화면이 들고 있는 표에 합친다.
 *
 * <b>물어본 키는 먼저 지운다.</b> 응답에 없다는 것은 "기록이 없다"는 뜻인데,
 * 합치기만 하면 다른 탭에서 지운 기록이 "수정" 버튼으로 남는다.
 */
export function mergeMyRecords(
  previous: ReadonlyMap<string, RecordResponse>,
  askedFor: ContentRefKey[],
  found: RecordResponse[],
): Map<string, RecordResponse> {
  const merged = new Map(previous)
  for (const content of askedFor) {
    merged.delete(contentKey(content))
  }
  for (const record of found) {
    merged.set(contentKey(record.content), record)
  }
  return merged
}
