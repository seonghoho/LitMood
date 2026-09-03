import { ApiError, type ProblemDetail } from '@litmood/api-client'

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080'

/**
 * 클라이언트 컴포넌트에서 백엔드를 호출한다.
 * 에러는 Problem Details 이므로 `code` 로 분기할 수 있는 ApiError 로 변환한다.
 */
export async function apiGet<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, {
    ...init,
    headers: { Accept: 'application/json', ...init?.headers },
    credentials: 'include',
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

  return (await response.json()) as T
}
