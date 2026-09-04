'use client'

import Link from 'next/link'
import { useCallback, useEffect, useState } from 'react'
import { css } from 'styled-system/css'
import { flex, stack } from 'styled-system/patterns'
import { ApiError } from '@litmood/api-client'
import { CONTENT_TYPE_LABEL, CONTENT_TYPES, type ContentType } from '@/features/content/types'
import { apiGet } from '@/shared/lib/api'
import { useAuthStore } from '@/shared/store/auth'
import { RecordCard } from './RecordCard'
import { STATUS_LABEL, type RecordPage, type RecordStatus } from './types'

const STATUSES: RecordStatus[] = ['WANT', 'DOING', 'DONE', 'DROPPED']

/** F-04-01·02 — 내 타임라인. 커서 기반 무한 스크롤과 필터. */
export function TimelineView() {
  // 필드별 구독 — 객체 셀렉터는 zustand v5 에서 무한 렌더를 유발한다
  const user = useAuthStore((state) => state.user)
  const ready = useAuthStore((state) => state.ready)

  const [items, setItems] = useState<RecordPage['items']>([])
  const [cursor, setCursor] = useState<string | null>(null)
  const [total, setTotal] = useState(0)
  const [typeFilter, setTypeFilter] = useState<ContentType | null>(null)
  const [statusFilter, setStatusFilter] = useState<RecordStatus | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(
    async (nextCursor: string | null, append: boolean) => {
      setLoading(true)
      setError(null)
      try {
        const params = new URLSearchParams()
        if (typeFilter) params.set('types', typeFilter)
        if (statusFilter) params.set('status', statusFilter)
        if (nextCursor) params.set('cursor', nextCursor)

        const page = await apiGet<RecordPage>(`/api/v1/records/me?${params.toString()}`)
        setItems((prev) => (append ? [...prev, ...page.items] : page.items))
        setCursor(page.nextCursor)
        setTotal(page.totalCount)
      } catch (e) {
        setError(e instanceof ApiError ? e.problem.title : '기록을 불러오지 못했습니다')
      } finally {
        setLoading(false)
      }
    },
    [typeFilter, statusFilter],
  )

  // 필터가 바뀌면 커서를 버리고 처음부터 다시 읽는다
  useEffect(() => {
    if (ready && user) {
      void load(null, false)
    }
  }, [ready, user, load])

  if (!ready) {
    return <p className={css({ textStyle: 'caption', color: 'fg.muted' })}>불러오는 중…</p>
  }

  if (!user) {
    return (
      <div className={stack({ gap: '4', alignItems: 'flex-start' })}>
        <p className={css({ textStyle: 'body', color: 'fg.default' })}>
          기록을 보려면 로그인이 필요합니다.
        </p>
        <Link
          href="/login?next=/timeline"
          className={css({
            textStyle: 'body',
            fontWeight: '600',
            px: '4',
            py: '2',
            rounded: 'md',
            bg: 'brand.default',
            color: 'fg.onAccent',
          })}
        >
          로그인
        </Link>
      </div>
    )
  }

  return (
    <div className={stack({ gap: '5' })}>
      <div className={stack({ gap: '2' })}>
        <div className={flex({ gap: '1.5', flexWrap: 'wrap' })}>
          <FilterChip selected={typeFilter === null} onClick={() => setTypeFilter(null)}>
            전체
          </FilterChip>
          {CONTENT_TYPES.map((type) => (
            <FilterChip
              key={type}
              selected={typeFilter === type}
              onClick={() => setTypeFilter(typeFilter === type ? null : type)}
            >
              {CONTENT_TYPE_LABEL[type]}
            </FilterChip>
          ))}
        </div>
        <div className={flex({ gap: '1.5', flexWrap: 'wrap' })}>
          {STATUSES.map((status) => (
            <FilterChip
              key={status}
              selected={statusFilter === status}
              onClick={() => setStatusFilter(statusFilter === status ? null : status)}
            >
              {STATUS_LABEL[status]}
            </FilterChip>
          ))}
        </div>
      </div>

      <p className={css({ textStyle: 'caption', color: 'fg.muted' })}>{total}개의 기록</p>

      {error && (
        <p role="alert" className={css({ textStyle: 'body', color: 'danger.500' })}>
          {error}
        </p>
      )}

      {!loading && items.length === 0 && (
        <div className={stack({ gap: '3', alignItems: 'flex-start', py: '8' })}>
          <p className={css({ textStyle: 'body', color: 'fg.muted' })}>아직 기록이 없습니다.</p>
          <Link
            href="/search"
            className={css({
              textStyle: 'body',
              fontWeight: '600',
              px: '4',
              py: '2',
              rounded: 'md',
              bg: 'brand.default',
              color: 'fg.onAccent',
            })}
          >
            첫 기록 남기기
          </Link>
        </div>
      )}

      <div className={stack({ gap: '3' })}>
        {items.map((record) => (
          <RecordCard key={record.id} record={record} />
        ))}
      </div>

      {cursor && (
        <button
          type="button"
          onClick={() => void load(cursor, true)}
          disabled={loading}
          className={css({
            textStyle: 'body',
            px: '4',
            py: '3',
            rounded: 'md',
            cursor: 'pointer',
            bg: 'bg.subtle',
            color: 'fg.default',
            _disabled: { opacity: 0.6 },
          })}
        >
          {loading ? '불러오는 중…' : '더 보기'}
        </button>
      )}
    </div>
  )
}

function FilterChip({
  selected,
  onClick,
  children,
}: {
  selected: boolean
  onClick: () => void
  children: React.ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={selected}
      className={css({
        textStyle: 'caption',
        px: '3',
        py: '1.5',
        rounded: 'full',
        cursor: 'pointer',
        borderWidth: '1px',
        borderStyle: 'solid',
        borderColor: selected ? 'brand.default' : 'border.default',
        bg: selected ? 'brand.default' : 'bg.surface',
        color: selected ? 'fg.onAccent' : 'fg.muted',
      })}
    >
      {children}
    </button>
  )
}
