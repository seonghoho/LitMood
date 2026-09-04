/**
 * Next.js App Router 는 동적 세그먼트를 <b>퍼센트 인코딩된 상태 그대로</b> 넘겨준다.
 *
 * 한글 slug(`비-오는-날-읽는-책-ab12cd`)를 그대로 encodeURIComponent 하면
 * `%` 가 다시 `%25` 로 인코딩되어 백엔드가 찾지 못한다.
 * ASCII 값만 다룰 때는 드러나지 않다가 한글이 들어오는 순간 404 가 된다.
 *
 * 이미 디코딩된 값이 올 가능성도 있으므로 실패해도 원본을 그대로 돌려준다.
 */
export function decodeRouteParam(raw: string): string {
  try {
    return decodeURIComponent(raw)
  } catch {
    return raw
  }
}
