'use client'

import Link from 'next/link'
import { notFound } from 'next/navigation'
import { useCallback, useEffect, useState } from 'react'
import { css } from 'styled-system/css'
import { flex, stack } from 'styled-system/patterns'
import { ApiError } from '@litmood/api-client'
import { REPORT_REASON_LABEL } from '@/features/social/report'
import { apiGet, apiPatch } from '@/shared/lib/api'
import { useAuthStore } from '@/shared/store/auth'
import { TARGET_LABEL, targetHref } from './types'
import type { AdminReportPage, AdminReportResponse, ReportStatus } from './types'

type Filter = ReportStatus | 'ALL'

const FILTERS: { value: Filter; label: string }[] = [
  { value: 'PENDING', label: '미처리' },
  { value: 'REVIEWED', label: '조치함' },
  { value: 'DISMISSED', label: '기각' },
  { value: 'ALL', label: '전체' },
]

const STATUS_LABEL: Record<ReportStatus, string> = {
  PENDING: '미처리',
  REVIEWED: '조치함',
  DISMISSED: '기각',
}

/**
 * 신고 처리 큐 (#28).
 *
 * 목록은 신고 건별이다. 같은 대상에 여러 건이 쌓였으면 배지로 알려주되 묶지는
 * 않는다 — 사유가 서로 달라 접으면 판단 근거가 사라진다.
 *
 * 처리는 상태를 바꾸는 것까지다. 기록 숨김·계정 정지 같은 조치는 아직 없으므로,
 * 대상 링크로 넘어가 손으로 확인한다.
 */
export function ReportQueue() {
  const user = useAuthStore((state) => state.user)
  const ready = useAuthStore((state) => state.ready)

  const [filter, setFilter] = useState<Filter>('PENDING')
  const [items, setItems] = useState<AdminReportResponse[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [pendingCount, setPendingCount] = useState(0)
  const [totalCount, setTotalCount] = useState(0)
  const [loading, setLoading] = useState(true)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)

  const isAdmin = Boolean(user?.admin)

  const load = useCallback(async (next: Filter, cursor: string | null) => {
    setError(null)
    const params = new URLSearchParams()
    if (next !== 'ALL') params.set('status', next)
    if (cursor) params.set('cursor', cursor)
    try {
      const page = await apiGet<AdminReportPage>(`/api/v1/admin/reports?${params}`)
      setItems((prev) => (cursor ? [...prev, ...page.items] : page.items))
      setNextCursor(page.nextCursor)
      setPendingCount(page.pendingCount)
      setTotalCount(page.totalCount)
    } catch (e) {
      setError(e instanceof ApiError ? e.problem.title : '신고 목록을 불러오지 못했습니다')
    }
  }, [])

  useEffect(() => {
    if (!ready || !isAdmin) return
    setLoading(true)
    void load(filter, null).finally(() => setLoading(false))
  }, [ready, isAdmin, filter, load])

  // 운영자가 아니면 이 화면은 없는 것이다 — 403 은 화면의 존재를 알려준다 (백엔드도 404 를 준다)
  if (ready && !isAdmin) notFound()
  if (!ready) return null

  const resolve = async (id: number, status: Extract<ReportStatus, 'REVIEWED' | 'DISMISSED'>) => {
    setBusyId(id)
    setError(null)
    try {
      const updated = await apiPatch<AdminReportResponse>(`/api/v1/admin/reports/${id}`, { status })
      setPendingCount((prev) => Math.max(0, prev - 1))
      // 지금 보는 필터에 더 이상 맞지 않으면 목록에서 뺀다
      setItems((prev) =>
        filter === 'ALL'
          ? prev.map((item) => (item.id === id ? updated : item))
          : prev.filter((item) => item.id !== id),
      )
    } catch (e) {
      setError(e instanceof ApiError ? e.problem.title : '신고를 처리하지 못했습니다')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <section className={stack({ gap: '4' })}>
      <div className={flex({ gap: '3', alignItems: 'baseline', justifyContent: 'space-between' })}>
        <h1 className={css({ textStyle: 'title' })}>신고 처리</h1>
        <p className={css({ textStyle: 'caption', color: 'fg.muted' })}>미처리 {pendingCount}건</p>
      </div>

      <div role="tablist" className={flex({ gap: '2' })}>
        {FILTERS.map((option) => (
          <button
            key={option.value}
            type="button"
            role="tab"
            aria-selected={filter === option.value}
            onClick={() => setFilter(option.value)}
            className={css({
              textStyle: 'caption',
              px: '3',
              py: '1.5',
              rounded: 'md',
              cursor: 'pointer',
              borderWidth: '1px',
              borderStyle: 'solid',
              borderColor: filter === option.value ? 'brand.default' : 'border.default',
              bg: filter === option.value ? 'brand.default' : 'transparent',
              color: filter === option.value ? 'fg.onAccent' : 'fg.muted',
            })}
          >
            {option.label}
          </button>
        ))}
      </div>

      {error && (
        <p role="alert" className={css({ textStyle: 'caption', color: 'danger.500' })}>
          {error}
        </p>
      )}

      {loading ? (
        <p className={css({ textStyle: 'caption', color: 'fg.muted' })}>불러오는 중…</p>
      ) : items.length === 0 ? (
        <p className={css({ textStyle: 'caption', color: 'fg.muted' })}>
          {filter === 'PENDING' ? '처리할 신고가 없습니다.' : '해당하는 신고가 없습니다.'}
        </p>
      ) : (
        <>
          <ul className={stack({ gap: '3' })}>
            {items.map((report) => (
              <ReportRow
                key={report.id}
                report={report}
                busy={busyId === report.id}
                onResolve={resolve}
              />
            ))}
          </ul>

          <p className={css({ textStyle: 'caption', color: 'fg.muted' })}>
            {items.length} / {totalCount}건
          </p>

          {nextCursor && (
            <button
              type="button"
              onClick={() => void load(filter, nextCursor)}
              className={css({
                textStyle: 'caption',
                py: '2',
                rounded: 'md',
                cursor: 'pointer',
                borderWidth: '1px',
                borderStyle: 'solid',
                borderColor: 'border.default',
                bg: 'transparent',
                color: 'fg.default',
              })}
            >
              더 보기
            </button>
          )}
        </>
      )}
    </section>
  )
}

function ReportRow({
  report,
  busy,
  onResolve,
}: {
  report: AdminReportResponse
  busy: boolean
  onResolve: (id: number, status: 'REVIEWED' | 'DISMISSED') => Promise<void>
}) {
  const href = targetHref(report.target)
  const label = report.target.label ?? '(삭제됨)'

  return (
    <li
      className={stack({
        gap: '2',
        p: '4',
        rounded: 'lg',
        borderWidth: '1px',
        borderStyle: 'solid',
        borderColor: 'border.default',
        bg: 'bg.surface',
      })}
    >
      <div className={flex({ gap: '2', alignItems: 'center', flexWrap: 'wrap' })}>
        <span className={css({ textStyle: 'caption', fontWeight: '600', color: 'fg.default' })}>
          {REPORT_REASON_LABEL[report.reason]}
        </span>
        <span className={css({ textStyle: 'caption', color: 'fg.muted' })}>
          {TARGET_LABEL[report.target.type]}
        </span>
        {report.sameTargetCount > 1 && (
          <span
            className={css({
              textStyle: 'caption',
              px: '2',
              rounded: 'full',
              bg: 'danger.500',
              color: 'fg.onAccent',
            })}
          >
            같은 대상 {report.sameTargetCount}건
          </span>
        )}
        {report.status !== 'PENDING' && (
          <span className={css({ textStyle: 'caption', color: 'fg.muted' })}>
            {STATUS_LABEL[report.status]}
          </span>
        )}
      </div>

      <p className={css({ textStyle: 'body', color: 'fg.default' })}>
        {href ? (
          <Link href={href} className={css({ textDecoration: 'underline' })}>
            {label}
          </Link>
        ) : (
          <span className={css({ color: 'fg.muted' })}>{label}</span>
        )}
        {report.target.handle && report.target.type !== 'USER' && (
          <span className={css({ textStyle: 'caption', color: 'fg.muted' })}>
            {' '}
            · @{report.target.handle}
          </span>
        )}
      </p>

      {report.detail && (
        <p className={css({ textStyle: 'caption', color: 'fg.muted' })}>{report.detail}</p>
      )}

      <p className={css({ textStyle: 'caption', color: 'fg.muted' })}>
        {report.reporterHandle ? `@${report.reporterHandle}` : '(탈퇴한 사용자)'} 신고 ·{' '}
        {new Date(report.createdAt).toLocaleString('ko-KR')}
      </p>

      {report.status === 'PENDING' && (
        <div className={flex({ gap: '2' })}>
          <ResolveButton
            onClick={() => void onResolve(report.id, 'REVIEWED')}
            disabled={busy}
            emphasis
          >
            조치함
          </ResolveButton>
          <ResolveButton onClick={() => void onResolve(report.id, 'DISMISSED')} disabled={busy}>
            기각
          </ResolveButton>
        </div>
      )}
    </li>
  )
}

function ResolveButton({
  onClick,
  disabled,
  emphasis,
  children,
}: {
  onClick: () => void
  disabled: boolean
  emphasis?: boolean
  children: React.ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={css({
        textStyle: 'caption',
        px: '3',
        py: '1.5',
        rounded: 'md',
        cursor: 'pointer',
        borderWidth: '1px',
        borderStyle: 'solid',
        borderColor: emphasis ? 'brand.default' : 'border.default',
        bg: emphasis ? 'brand.default' : 'transparent',
        color: emphasis ? 'fg.onAccent' : 'fg.muted',
        _disabled: { opacity: 0.5, cursor: 'not-allowed' },
      })}
    >
      {children}
    </button>
  )
}
