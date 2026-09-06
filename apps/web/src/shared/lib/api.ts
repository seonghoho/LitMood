import { ApiError, type ProblemDetail } from '@litmood/api-client'
import { useAuthStore } from '@/shared/store/auth'

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080'

async function request<T>(
  path: string,
  init?: RequestInit,
  retryOnUnauthorized = true,
): Promise<T> {
  const token = useAuthStore.getState().accessToken

  const headers = new Headers(init?.headers)
  headers.set('Accept', 'application/json')
  if (init?.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const response = await fetch(`${BASE_URL}${path}`, { ...init, headers, credentials: 'include' })

  if (response.status === 401 && retryOnUnauthorized && token) {
    // Access 토큰이 만료됐을 뿐일 수 있다. refresh 쿠키로 한 번만 재발급을 시도한다.
    const refreshed = await tryRefresh()
    if (refreshed) {
      return request<T>(path, init, false)
    }
  }

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

  // 본문이 없는 성공 응답이 204 뿐이라고 볼 수 없다 — 신고 접수는 202 에 빈 본문이다.
  // 있지도 않은 JSON 을 파싱하면 SyntaxError 가 나고, 호출부는 성공한 요청을 실패로 읽는다.
  const contentType = response.headers.get('content-type')
  if (response.status === 204 || !contentType?.includes('json')) {
    return undefined as T
  }
  return (await response.json()) as T
}

/** refresh 쿠키로 액세스 토큰 재발급. 실패하면 로그아웃 상태로 전환한다. */
export async function tryRefresh(): Promise<boolean> {
  try {
    const response = await fetch(`${BASE_URL}/api/v1/auth/refresh`, {
      method: 'POST',
      credentials: 'include',
    })
    if (!response.ok) {
      useAuthStore.getState().signOut()
      return false
    }
    const data = (await response.json()) as { accessToken: string; user: CurrentUserPayload }
    useAuthStore.getState().signIn(data.accessToken, data.user)
    return true
  } catch {
    useAuthStore.getState().signOut()
    return false
  }
}

interface CurrentUserPayload {
  id: number
  handle: string
  nickname: string
  avatarUrl: string | null
}

export const apiGet = <T>(path: string, init?: RequestInit) =>
  request<T>(path, { ...init, method: 'GET' })

export const apiPost = <T>(path: string, body?: unknown) =>
  request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) })

export const apiPatch = <T>(path: string, body: unknown) =>
  request<T>(path, { method: 'PATCH', body: JSON.stringify(body) })

// 204 면 본문이 없고, 좋아요 취소처럼 갱신된 상태를 돌려주는 삭제도 있다.
// 기본 타입이 void 라 본문을 쓰지 않는 호출부는 그대로 둔다.
export const apiDelete = <T = void>(path: string) => request<T>(path, { method: 'DELETE' })
