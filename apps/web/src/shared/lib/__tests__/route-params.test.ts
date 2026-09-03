import { describe, expect, it } from 'vitest'
import { decodeRouteParam } from '../route-params'

/**
 * M3 에서 실제로 터진 버그의 회귀 방지.
 *
 * Next App Router 는 동적 세그먼트를 퍼센트 인코딩된 채로 넘긴다.
 * 여기에 encodeURIComponent 를 다시 걸면 %가 %25 로 이중 인코딩되어
 * 백엔드가 찾지 못한다. ASCII 값에서는 드러나지 않다가 한글에서 404 가 된다.
 */
describe('decodeRouteParam', () => {
  it('퍼센트 인코딩된 한글 slug 를 복원한다', () => {
    expect(decodeRouteParam('%EB%B9%84-%EC%98%A4%EB%8A%94-%EB%82%A0-ab12cd')).toBe(
      '비-오는-날-ab12cd',
    )
  })

  it('이미 디코딩된 값은 그대로 둔다', () => {
    expect(decodeRouteParam('비-오는-날-ab12cd')).toBe('비-오는-날-ab12cd')
  })

  it('ASCII slug 는 변하지 않는다', () => {
    expect(decodeRouteParam('rainy-day-albums-ab12cd')).toBe('rainy-day-albums-ab12cd')
  })

  it('잘못된 인코딩이 와도 예외 대신 원본을 돌려준다', () => {
    // 라우트 파라미터 하나 때문에 페이지 전체가 500 이 되면 안 된다
    expect(decodeRouteParam('%E0%A4%A')).toBe('%E0%A4%A')
    expect(decodeRouteParam('100%')).toBe('100%')
  })
})
