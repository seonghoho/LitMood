import { describe, expect, it } from 'vitest'
import { buildRecordPatch, type RecordDraft } from '../record-edit'
import type { RecordResponse } from '../types'

/**
 * 백엔드 PATCH 규칙(이슈 #5)에 맞춘다.
 * 넣지 않은 필드는 변경되지 않고, 지우려면 빈 문자열 또는 clearRating 을 쓴다.
 */
const saved = {
  status: 'DONE',
  rating: 4.5,
  moods: [{ displayName: '새벽' }, { displayName: '먹먹함' }],
  review: '처음 읽었을 때의 기록',
  visibility: 'PUBLIC',
  isSpoiler: false,
  contextNote: '지하철에서',
  startedAt: '2026-01-01',
  finishedAt: '2026-01-05',
  repeatCount: 1,
} as unknown as RecordResponse

const draftOf = (record: RecordResponse): RecordDraft => ({
  status: record.status,
  rating: record.rating,
  moods: record.moods.map((m) => m.displayName),
  review: record.review ?? '',
  visibility: record.visibility,
  isSpoiler: record.isSpoiler,
  contextNote: record.contextNote ?? '',
  startedAt: record.startedAt ?? '',
  finishedAt: record.finishedAt ?? '',
  repeatCount: record.repeatCount,
})

describe('buildRecordPatch', () => {
  it('바뀐 것이 없으면 빈 객체를 돌려준다', () => {
    expect(buildRecordPatch(saved, draftOf(saved))).toEqual({})
  })

  it('바뀐 필드만 담는다 — 별점만 고쳐도 리뷰는 보내지 않는다', () => {
    expect(buildRecordPatch(saved, { ...draftOf(saved), rating: 3 })).toEqual({ rating: 3 })
  })

  it('별점을 비우면 clearRating 으로 보낸다 — rating: null 은 서버가 "변경 없음"으로 읽는다', () => {
    expect(buildRecordPatch(saved, { ...draftOf(saved), rating: null })).toEqual({
      clearRating: true,
    })
  })

  it('리뷰를 비우면 빈 문자열로 보낸다 — 생략하면 지워지지 않는다', () => {
    expect(buildRecordPatch(saved, { ...draftOf(saved), review: '' })).toEqual({ review: '' })
  })

  it('리뷰의 앞뒤 공백만 다른 것은 변경으로 보지 않는다', () => {
    expect(
      buildRecordPatch(saved, { ...draftOf(saved), review: '  처음 읽었을 때의 기록  ' }),
    ).toEqual({})
  })

  it('무드는 전체 교체라 하나만 바뀌어도 전부 담는다', () => {
    expect(buildRecordPatch(saved, { ...draftOf(saved), moods: ['새벽'] })).toEqual({
      moods: ['새벽'],
    })
  })

  it('무드 순서만 바뀐 것은 변경으로 보지 않는다', () => {
    expect(buildRecordPatch(saved, { ...draftOf(saved), moods: ['먹먹함', '새벽'] })).toEqual({})
  })

  it('WANT 로 바꾸면 상태만 보낸다 — 별점은 서버가 규칙대로 지운다', () => {
    expect(buildRecordPatch(saved, { ...draftOf(saved), status: 'WANT', rating: null })).toEqual({
      status: 'WANT',
    })
  })

  it('장소 메모를 비우면 빈 문자열로 보낸다', () => {
    expect(buildRecordPatch(saved, { ...draftOf(saved), contextNote: '' })).toEqual({
      contextNote: '',
    })
  })

  it('날짜를 비우면 clear 플래그로 보낸다 — 빈 문자열은 날짜가 아니다', () => {
    expect(buildRecordPatch(saved, { ...draftOf(saved), startedAt: '' })).toEqual({
      clearStartedAt: true,
    })
    expect(buildRecordPatch(saved, { ...draftOf(saved), finishedAt: '' })).toEqual({
      clearFinishedAt: true,
    })
  })

  it('날짜를 바꾸면 새 값만 보낸다', () => {
    expect(buildRecordPatch(saved, { ...draftOf(saved), startedAt: '2026-02-01' })).toEqual({
      startedAt: '2026-02-01',
    })
  })

  it('스포일러 토글과 재소비 횟수도 담는다', () => {
    expect(buildRecordPatch(saved, { ...draftOf(saved), isSpoiler: true })).toEqual({
      isSpoiler: true,
    })
    expect(buildRecordPatch(saved, { ...draftOf(saved), repeatCount: 3 })).toEqual({
      repeatCount: 3,
    })
  })

  it('여러 필드가 바뀌면 모두 담는다', () => {
    expect(
      buildRecordPatch(saved, {
        ...draftOf(saved),
        status: 'DROPPED',
        rating: 2,
        moods: ['지침'],
        review: '끝까지 못 읽었다',
        visibility: 'PRIVATE',
      }),
    ).toEqual({
      status: 'DROPPED',
      rating: 2,
      moods: ['지침'],
      review: '끝까지 못 읽었다',
      visibility: 'PRIVATE',
    })
  })
})
