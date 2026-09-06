import { describe, expect, it } from 'vitest'
import { parseViewMode } from '../view-mode'

describe('parseViewMode', () => {
  it('저장된 값이 grid 면 그리드다', () => {
    expect(parseViewMode('grid')).toBe('grid')
  })

  it.each([null, '', 'list', 'GRID', '{"mode":"grid"}', 'true'])(
    '그 밖의 값(%s)은 리스트로 본다 — 남이 남긴 값일 수도 있다',
    (raw) => {
      expect(parseViewMode(raw)).toBe('list')
    },
  )
})
