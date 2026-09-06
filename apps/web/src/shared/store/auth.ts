'use client'

import { create } from 'zustand'
import { setAccessToken, type UserSummary } from '@litmood/api-client'

/**
 * 로그인한 사용자. 정의는 백엔드 DTO 이고 OpenAPI 를 거쳐 온다 (ADR-008) —
 * 손으로 베껴 두면 `admin` 같은 필드가 늘 때 화면만 모르는 채로 남는다.
 */
export type CurrentUser = UserSummary

interface AuthState {
  user: CurrentUser | null
  accessToken: string | null
  ready: boolean
  signIn: (token: string, user: CurrentUser) => void
  signOut: () => void
  markReady: () => void
}

/**
 * ADR-009 — Access 토큰은 메모리에만 둔다.
 *
 * localStorage 에 두면 XSS 한 번에 탈취된다. 새로고침 시 토큰이 사라지지만,
 * refresh 쿠키(HttpOnly)로 재발급받으면 되므로 사용자 경험 손실은 없다.
 */
export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  accessToken: null,
  ready: false,
  signIn: (token, user) => {
    setAccessToken(token)
    set({ accessToken: token, user, ready: true })
  },
  signOut: () => {
    setAccessToken(null)
    set({ accessToken: null, user: null, ready: true })
  },
  markReady: () => set({ ready: true }),
}))
