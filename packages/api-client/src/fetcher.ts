/**
 * orval 이 생성한 모든 훅이 사용하는 공통 fetcher.
 *
 * - Access 토큰은 localStorage 에 두지 않는다 (ADR-009). 메모리 또는 서버 컴포넌트에서 주입한다.
 * - 에러는 RFC 9457 Problem Details 로 오므로 `code` 로 분기 가능한 형태로 변환한다.
 */

export interface ProblemDetail {
  type: string
  title: string
  status: number
  detail?: string
  instance?: string
  code: string
  errors?: Array<{ field: string; reason: string }>
}

export class ApiError extends Error {
  constructor(readonly problem: ProblemDetail) {
    super(problem.title)
    this.name = 'ApiError'
  }

  get code(): string {
    return this.problem.code
  }
}

let accessToken: string | null = null

export function setAccessToken(token: string | null): void {
  accessToken = token
}

const BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ??
  process.env.API_INTERNAL_BASE_URL ??
  'http://localhost:8080'

/**
 * orval 이 생성한 호출 함수는 본문뿐 아니라 상태 코드와 헤더까지 함께 받는
 * `{ data, status, headers }` 형태를 기대한다. 본문만 돌려주면 호출부의
 * `const { data } = await get(...)` 이 undefined 를 집는다.
 */
export interface ApiResult<T> {
  data: T
  status: number
  headers: Headers
}

export const apiFetcher = async <T extends ApiResult<unknown>>(
  url: string,
  init?: RequestInit,
): Promise<T> => {
  const headers = new Headers(init?.headers)
  headers.set('Accept', 'application/json')
  if (init?.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }

  const response = await fetch(`${BASE_URL}${url}`, {
    ...init,
    headers,
    credentials: 'include', // refresh 토큰 쿠키 전송
  })

  if (!response.ok) {
    const problem = (await response.json().catch(() => null)) as ProblemDetail | null
    throw new ApiError(
      problem ?? {
        type: 'about:blank',
        title: '요청을 처리하지 못했습니다',
        status: response.status,
        code: 'INTERNAL_ERROR',
      },
    )
  }

  // 204 는 본문이 없다. json() 을 부르면 파싱 에러가 난다.
  const data = response.status === 204 ? undefined : await response.json()
  return { data, status: response.status, headers: response.headers } as T
}
