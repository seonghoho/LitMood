import type { Metadata } from 'next'
import { Suspense } from 'react'
import { css } from 'styled-system/css'
import { stack } from 'styled-system/patterns'
import { AuthForm } from '@/features/auth/AuthForm'

export const metadata: Metadata = { title: '로그인' }

export default function LoginPage() {
  return (
    <main className={stack({ gap: '6', maxW: '380px', mx: 'auto', px: '6', py: '16' })}>
      <h1 className={css({ textStyle: 'title' })}>로그인</h1>
      {/* useSearchParams 는 Suspense 경계를 요구한다 */}
      <Suspense fallback={null}>
        <AuthForm mode="login" />
      </Suspense>
    </main>
  )
}
