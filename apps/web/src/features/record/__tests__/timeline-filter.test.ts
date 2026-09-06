import { describe, expect, it } from 'vitest'
import {
  buildTimelineQuery,
  EMPTY_TIMELINE_FILTER,
  isFilterActive,
  toggleMoodFilter,
  type TimelineFilter,
} from '../timeline-filter'

const filter = (overrides: Partial<TimelineFilter> = {}): TimelineFilter => ({
  ...EMPTY_TIMELINE_FILTER,
  ...overrides,
})

describe('buildTimelineQuery', () => {
  it('아무것도 고르지 않으면 빈 쿼리다 — 서버가 전체를 준다', () => {
    expect(buildTimelineQuery(EMPTY_TIMELINE_FILTER, null)).toBe('')
  })

  it('"지정 안 함"인 빈 문자열은 실어 보내지 않는다', () => {
    // from='' 을 그대로 보내면 서버의 LocalDate 파싱이 400 으로 떨어진다
    expect(buildTimelineQuery(filter({ from: '', to: '' }), null)).toBe('')
  })

  it('무드 여러 개는 반복 파라미터로 나간다 — 서버는 "그중 하나라도" 로 본다', () => {
    const query = buildTimelineQuery(filter({ moods: ['새벽', '위로'] }), null)
    expect(new URLSearchParams(query).getAll('moods')).toEqual(['새벽', '위로'])
  })

  it('별점은 BigDecimal 로 받으므로 소수점을 붙인다', () => {
    expect(buildTimelineQuery(filter({ minRating: 3 }), null)).toBe('minRating=3.0')
    expect(buildTimelineQuery(filter({ minRating: 4.5 }), null)).toBe('minRating=4.5')
  })

  it('별점 0.5 는 유효한 값이다 — falsy 라고 빠뜨리면 안 된다', () => {
    expect(buildTimelineQuery(filter({ minRating: 0.5 }), null)).toBe('minRating=0.5')
  })

  it('커서는 필터와 함께 나간다', () => {
    const query = buildTimelineQuery(filter({ type: 'BOOK' }), 'abc123')
    const params = new URLSearchParams(query)
    expect(params.get('types')).toBe('BOOK')
    expect(params.get('cursor')).toBe('abc123')
  })
})

describe('toggleMoodFilter', () => {
  it('없으면 넣고 있으면 뺀다', () => {
    const once = toggleMoodFilter(EMPTY_TIMELINE_FILTER, '새벽')
    expect(once.moods).toEqual(['새벽'])
    expect(toggleMoodFilter(once, '새벽').moods).toEqual([])
  })

  it('원본을 건드리지 않는다 — 상태 갱신이 새 객체여야 다시 조회된다', () => {
    const before = filter({ moods: ['새벽'] })
    toggleMoodFilter(before, '위로')
    expect(before.moods).toEqual(['새벽'])
  })
})

describe('isFilterActive', () => {
  it('빈 필터는 비활성이다', () => {
    expect(isFilterActive(EMPTY_TIMELINE_FILTER)).toBe(false)
  })

  it.each([
    ['타입', filter({ type: 'MOVIE' })],
    ['상태', filter({ status: 'DONE' })],
    ['무드', filter({ moods: ['새벽'] })],
    ['별점', filter({ minRating: 0.5 })],
    ['시작일', filter({ from: '2026-01-01' })],
    ['종료일', filter({ to: '2026-01-01' })],
  ])('%s 하나만 걸려도 활성이다', (_label, value) => {
    expect(isFilterActive(value)).toBe(true)
  })
})
