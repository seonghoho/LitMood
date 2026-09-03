import { describe, expect, it } from 'vitest'
import { normalizeMoodName } from '@litmood/ui'

/**
 * 무드 이름 정규화는 백엔드(Mood.normalize)와 <b>완전히 같은 규칙</b>이어야 한다.
 *
 * 규칙이 어긋나면 프론트에서 "같은 무드"로 보이는 것이 서버에서는 다른 행으로
 * 저장되어 태그가 갈라진다. 무드는 이 서비스의 1급 개념이라 갈라지면 치명적이다.
 *
 * 아래 기대값은 백엔드의 RecordDomainTest 와 동일한 케이스다.
 * 한쪽 규칙을 바꾸면 양쪽 테스트를 함께 고쳐야 한다.
 */
describe('normalizeMoodName — 백엔드 Mood.normalize 와 동일 규칙', () => {
  it.each([
    ['#새벽', '새벽'],
    ['  새벽  ', '새벽'],
    ['새 벽', '새벽'],
    ['Dawn', 'dawn'],
    ['#Rainy Day', 'rainyday'],
  ])('"%s" → "%s"', (input, expected) => {
    expect(normalizeMoodName(input)).toBe(expected)
  })

  it('앞의 # 만 제거한다 — 중간의 # 는 이름의 일부다', () => {
    expect(normalizeMoodName('#a#b')).toBe('a#b')
  })

  it('연속 공백도 모두 제거한다', () => {
    expect(normalizeMoodName('가   나')).toBe('가나')
  })
})
