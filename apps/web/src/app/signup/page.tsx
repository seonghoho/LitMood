import type { Metadata } from 'next'
import { Suspense } from 'react'
import { css } from 'styled-system/css'
import { stack } from 'styled-system/patterns'
import { AuthForm } from '@/features/auth/AuthForm'

export const metadata: Metadata = { title: '가입하기' }

export default function SignupPage() {
  return (
    <main className={stack({ gap: '6', maxW: '380px', mx: 'auto', px: '6', py: '16' })}>
      <h1 className={css({ textStyle: 'title' })}>가입하기</h1>
      <Suspense fallback={null}>
        <AuthForm mode="signup" />
      </Suspense>
    </main>
  )
}
