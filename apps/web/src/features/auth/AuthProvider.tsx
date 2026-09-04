'use client'

import { useEffect, type ReactNode } from 'react'
import { tryRefresh } from '@/shared/lib/api'
import { useAuthStore } from '@/shared/store/auth'

/**
 * 앱 시작 시 세션 복구.
 *
 * Access 토큰은 메모리에만 있어 새로고침하면 사라진다(ADR-009).
 * HttpOnly refresh 쿠키로 조용히 재발급해 로그인 상태를 이어준다.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const markReady = useAuthStore((state) => state.markReady)

  useEffect(() => {
    tryRefresh().finally(markReady)
  }, [markReady])

  return <>{children}</>
}
