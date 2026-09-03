'use client'

import Link from 'next/link'
import { useRouter, useSearchParams } from 'next/navigation'
import { useState } from 'react'
import { css } from 'styled-system/css'
import { stack } from 'styled-system/patterns'
import { ApiError } from '@litmood/api-client'
import { apiPost } from '@/shared/lib/api'
import { useAuthStore, type CurrentUser } from '@/shared/store/auth'

interface AuthResult {
  accessToken: string
  expiresIn: number
  user: CurrentUser
}

export function AuthForm({ mode }: { mode: 'login' | 'signup' }) {
  const router = useRouter()
  const searchParams = useSearchParams()
  const signIn = useAuthStore((state) => state.signIn)

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [handle, setHandle] = useState('')
  const [nickname, setNickname] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const isSignup = mode === 'signup'

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    setFieldErrors({})

    try {
      const result = await apiPost<AuthResult>(
        isSignup ? '/api/v1/auth/signup' : '/api/v1/auth/login',
        isSignup ? { email, password, handle, nickname } : { email, password },
      )
      signIn(result.accessToken, result.user)
      // 기록하려다 로그인으로 밀려온 경우 하던 자리로 돌려보낸다
      router.push(searchParams.get('next') ?? '/timeline')
      router.refresh()
    } catch (e) {
      if (e instanceof ApiError) {
        setError(e.problem.title)
        // 서버가 필드별 사유를 주면 입력 옆에 그대로 보여준다
        const errors = e.problem.errors ?? []
        setFieldErrors(Object.fromEntries(errors.map((fe) => [fe.field, fe.reason])))
      } else {
        setError('요청을 처리하지 못했습니다')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={submit} className={stack({ gap: '4' })}>
      <TextField
        label="이메일"
        type="email"
        value={email}
        onChange={setEmail}
        error={fieldErrors.email}
        autoComplete="email"
        required
      />
      <TextField
        label="비밀번호"
        type="password"
        value={password}
        onChange={setPassword}
        error={fieldErrors.password}
        autoComplete={isSignup ? 'new-password' : 'current-password'}
        required
      />
      {isSignup && (
        <>
          <TextField
            label="아이디"
            value={handle}
            onChange={setHandle}
            error={fieldErrors.handle}
            hint="공개 프로필 주소가 됩니다 — /@아이디"
            required
          />
          <TextField
            label="닉네임"
            value={nickname}
            onChange={setNickname}
            error={fieldErrors.nickname}
            required
          />
        </>
      )}

      {error && (
        <p role="alert" className={css({ textStyle: 'caption', color: 'danger.500' })}>
          {error}
        </p>
      )}

      <button
        type="submit"
        disabled={submitting}
        className={css({
          textStyle: 'body',
          fontWeight: '600',
          px: '4',
          py: '3',
          rounded: 'md',
          cursor: 'pointer',
          bg: 'brand.default',
          color: 'fg.onAccent',
          _disabled: { opacity: 0.6, cursor: 'not-allowed' },
        })}
      >
        {submitting ? '처리 중…' : isSignup ? '가입하기' : '로그인'}
      </button>

      <p className={css({ textStyle: 'caption', color: 'fg.muted', textAlign: 'center' })}>
        {isSignup ? '이미 계정이 있나요? ' : '아직 계정이 없나요? '}
        <Link
          href={isSignup ? '/login' : '/signup'}
          className={css({ color: 'brand.default', textDecoration: 'underline' })}
        >
          {isSignup ? '로그인' : '가입하기'}
        </Link>
      </p>
    </form>
  )
}

function TextField({
  label,
  value,
  onChange,
  type = 'text',
  error,
  hint,
  autoComplete,
  required,
}: {
  label: string
  value: string
  onChange: (value: string) => void
  type?: string
  error?: string
  hint?: string
  autoComplete?: string
  required?: boolean
}) {
  return (
    <label className={stack({ gap: '1.5' })}>
      <span className={css({ textStyle: 'caption', color: 'fg.muted' })}>{label}</span>
      <input
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        autoComplete={autoComplete}
        required={required}
        aria-invalid={error ? true : undefined}
        className={css({
          px: '3',
          py: '2.5',
          rounded: 'md',
          textStyle: 'body',
          bg: 'bg.surface',
          color: 'fg.default',
          borderWidth: '1px',
          borderStyle: 'solid',
          borderColor: 'border.default',
          _focusVisible: {
            outlineWidth: '2px',
            outlineStyle: 'solid',
            outlineColor: 'brand.default',
          },
        })}
      />
      {error ? (
        <span className={css({ textStyle: 'caption', color: 'danger.500' })}>{error}</span>
      ) : hint ? (
        <span className={css({ textStyle: 'caption', color: 'fg.muted' })}>{hint}</span>
      ) : null}
    </label>
  )
}
