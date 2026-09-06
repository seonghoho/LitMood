'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import { css } from 'styled-system/css'
import { flex, stack } from 'styled-system/patterns'
import { ApiError } from '@litmood/api-client'
import { apiDelete, apiGet } from '@/shared/lib/api'
import { useAuthStore } from '@/shared/store/auth'

interface BlockedUser {
  handle: string
  nickname: string
  avatarUrl: string | null
  blockedAt: string
}

/**
 * 차단 목록 (F-06-05).
 *
 * 차단하면 상대 프로필의 목록이 가려지므로, 여기가 없으면 실수로 누른 차단을
 * 되돌릴 경로가 사라진다. 차단은 되돌릴 수 있어야 한다.
 */
export function BlockedUsers() {
  const user = useAuthStore((state) => state.user)
  const ready = useAuthStore((state) => state.ready)
  const [blocked, setBlocked] = useState<BlockedUser[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [busyHandle, setBusyHandle] = useState<string | null>(null)

  useEffect(() => {
    if (!ready || !user) return
    apiGet<BlockedUser[]>('/api/v1/users/me/blocks')
      .then(setBlocked)
      .catch((e: unknown) =>
        setError(e instanceof ApiError ? e.problem.title : '차단 목록을 불러오지 못했습니다'),
      )
      .finally(() => setLoading(false))
  }, [ready, user])

  const unblock = async (handle: string) => {
    setBusyHandle(handle)
    setError(null)
    try {
      await apiDelete(`/api/v1/users/@${encodeURIComponent(handle)}/block`)
      setBlocked((prev) => prev.filter((entry) => entry.handle !== handle))
    } catch (e) {
      setError(e instanceof ApiError ? e.problem.title : '차단을 해제하지 못했습니다')
    } finally {
      setBusyHandle(null)
    }
  }

  if (!ready || !user || loading) return null

  return (
    <section className={stack({ gap: '3' })}>
      <h2 className={css({ textStyle: 'title' })}>차단한 사용자</h2>

      {error && (
        <p role="alert" className={css({ textStyle: 'caption', color: 'danger.500' })}>
          {error}
        </p>
      )}

      {blocked.length === 0 ? (
        <p className={css({ textStyle: 'caption', color: 'fg.muted' })}>
          차단한 사용자가 없습니다.
        </p>
      ) : (
        <ul className={stack({ gap: '2' })}>
          {blocked.map((entry) => (
            <li
              key={entry.handle}
              className={flex({
                gap: '3',
                alignItems: 'center',
                justifyContent: 'space-between',
                p: '3',
                rounded: 'md',
                bg: 'bg.surface',
                borderWidth: '1px',
                borderStyle: 'solid',
                borderColor: 'border.default',
              })}
            >
              <div className={stack({ gap: '0.5' })}>
                {/* 차단 중에도 프로필로 갈 수는 있다 — 누구를 차단했는지 확인할 수 있어야 한다 */}
                <Link
                  href={`/@${entry.handle}`}
                  className={css({ textStyle: 'body', color: 'fg.default' })}
                >
                  {entry.nickname}
                </Link>
                <span className={css({ textStyle: 'caption', color: 'fg.muted' })}>
                  @{entry.handle}
                </span>
              </div>
              <button
                type="button"
                onClick={() => void unblock(entry.handle)}
                disabled={busyHandle === entry.handle}
                className={css({
                  textStyle: 'caption',
                  px: '3',
                  py: '1.5',
                  rounded: 'md',
                  cursor: 'pointer',
                  bg: 'bg.subtle',
                  color: 'fg.muted',
                  _disabled: { opacity: 0.6 },
                })}
              >
                {busyHandle === entry.handle ? '해제하는 중…' : '차단 해제'}
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
