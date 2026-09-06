import { describe, expect, it } from 'vitest'
import { buildByContentPath, contentKey, MAX_REFS_PER_REQUEST, mergeMyRecords } from '../my-records'
import type { RecordResponse } from '../types'

const ref = (externalId: string, provider: 'NAVER_BOOK' | 'TMDB' = 'NAVER_BOOK') => ({
  provider,
  externalId,
})

const record = (externalId: string, id = 1): RecordResponse =>
  ({
    id,
    content: { provider: 'NAVER_BOOK', externalId },
  }) as RecordResponse

describe('buildByContentPath', () => {
  it('볼 것이 없으면 요청 자체를 만들지 않는다', () => {
    expect(buildByContentPath([])).toBeNull()
  })

  it('여러 콘텐츠는 반복 파라미터로 나간다 — 결과 수만큼 왕복하지 않는다', () => {
    const path = buildByContentPath([ref('9788937473135'), ref('550', 'TMDB')])!

    const params = new URLSearchParams(path.split('?')[1])
    expect(params.getAll('refs')).toEqual(['NAVER_BOOK:9788937473135', 'TMDB:550'])
  })

  it('상한을 넘으면 앞에서 자른다 — 서버는 넘치면 통째로 400 이다', () => {
    const many = Array.from({ length: MAX_REFS_PER_REQUEST + 10 }, (_, i) => ref(`isbn-${i}`))

    const params = new URLSearchParams(buildByContentPath(many)!.split('?')[1])
    expect(params.getAll('refs')).toHaveLength(MAX_REFS_PER_REQUEST)
  })
})

describe('mergeMyRecords', () => {
  it('찾은 기록을 콘텐츠 좌표로 꽂아 둔다', () => {
    const merged = mergeMyRecords(new Map(), [ref('isbn-1')], [record('isbn-1', 7)])

    expect(merged.get(contentKey(ref('isbn-1')))?.id).toBe(7)
  })

  it('물어봤는데 응답에 없으면 지운다 — 다른 탭에서 지운 기록이 "수정" 으로 남으면 안 된다', () => {
    const previous = new Map([[contentKey(ref('isbn-1')), record('isbn-1')]])

    const merged = mergeMyRecords(previous, [ref('isbn-1')], [])

    expect(merged.has(contentKey(ref('isbn-1')))).toBe(false)
  })

  it('묻지 않은 것은 그대로 둔다 — 탭을 옮겨도 앞서 확인한 것이 사라지지 않는다', () => {
    const previous = new Map([[contentKey(ref('isbn-1')), record('isbn-1')]])

    const merged = mergeMyRecords(previous, [ref('550', 'TMDB')], [])

    expect(merged.has(contentKey(ref('isbn-1')))).toBe(true)
  })
})
