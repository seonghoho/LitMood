'use client'

import Link from 'next/link'
import { usePathname, useRouter } from 'next/navigation'
import { css } from 'styled-system/css'
import { flex } from 'styled-system/patterns'
import { apiPost } from '@/shared/lib/api'
import { useAuthStore } from '@/shared/store/auth'

export function NavBar() {
  const pathname = usePathname()
  const router = useRouter()
  // zustand v5 는 셀렉터 결과를 참조 동등성으로 비교한다.
  // 객체를 반환하면 매 렌더마다 새 참조가 되어 무한 루프에 빠지므로 필드별로 구독한다.
  const user = useAuthStore((state) => state.user)
  const ready = useAuthStore((state) => state.ready)
  const signOut = useAuthStore((state) => state.signOut)

  const logout = async () => {
    await apiPost('/api/v1/auth/logout').catch(() => undefined)
    signOut()
    router.push('/')
    router.refresh()
  }

  return (
    <header
      className={flex({
        gap: '4',
        alignItems: 'center',
        px: '6',
        py: '3',
        borderBottomWidth: '1px',
        borderBottomStyle: 'solid',
        borderColor: 'border.default',
        bg: 'bg.surface',
      })}
    >
      <Link href="/" className={css({ textStyle: 'body', fontWeight: '700', color: 'fg.default' })}>
        LitMood
      </Link>

      <nav className={flex({ gap: '3', flex: 1 })}>
        <NavLink href="/search" active={pathname === '/search'}>
          검색
        </NavLink>
        <NavLink href="/feed" active={pathname === '/feed'}>
          피드
        </NavLink>
        <NavLink href="/timeline" active={pathname === '/timeline'}>
          내 기록
        </NavLink>
        <NavLink href="/collections/new" active={pathname.startsWith('/collections')}>
          컬렉션
        </NavLink>
      </nav>

      {/* 세션 복구 전에는 아무것도 그리지 않는다 — 로그인/로그아웃이 깜빡이는 것을 막는다 */}
      {ready &&
        (user ? (
          <div className={flex({ gap: '3', alignItems: 'center' })}>
            <Link
              href={`/@${user.handle}`}
              className={css({ textStyle: 'caption', color: 'fg.default' })}
            >
              {user.nickname}
            </Link>
            <button
              type="button"
              onClick={() => void logout()}
              className={css({
                textStyle: 'caption',
                color: 'fg.muted',
                cursor: 'pointer',
                bg: 'transparent',
              })}
            >
              로그아웃
            </button>
          </div>
        ) : (
          <Link
            href="/login"
            className={css({
              textStyle: 'caption',
              fontWeight: '600',
              px: '3',
              py: '1.5',
              rounded: 'md',
              bg: 'brand.default',
              color: 'fg.onAccent',
            })}
          >
            로그인
          </Link>
        ))}
    </header>
  )
}

function NavLink({
  href,
  active,
  children,
}: {
  href: string
  active: boolean
  children: React.ReactNode
}) {
  return (
    <Link
      href={href}
      aria-current={active ? 'page' : undefined}
      className={css({
        textStyle: 'caption',
        color: active ? 'fg.default' : 'fg.muted',
        fontWeight: active ? '600' : '400',
      })}
    >
      {children}
    </Link>
  )
}
