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

export const apiFetcher = async <T>(url: string, init?: RequestInit): Promise<T> => {
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

  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}
