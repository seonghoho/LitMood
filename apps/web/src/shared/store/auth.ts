'use client'

import { create } from 'zustand'
import { setAccessToken } from '@litmood/api-client'

export interface CurrentUser {
  id: number
  handle: string
  nickname: string
  avatarUrl: string | null
}

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
