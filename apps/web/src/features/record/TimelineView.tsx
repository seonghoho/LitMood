'use client'

import Link from 'next/link'
import { useCallback, useEffect, useState } from 'react'
import { css } from 'styled-system/css'
import { flex, grid, stack } from 'styled-system/patterns'
import { ApiError } from '@litmood/api-client'
import { FALLBACK_MOOD_COLOR } from '@litmood/ui'
import { CONTENT_TYPE_LABEL, CONTENT_TYPES } from '@/features/content/types'
import { apiGet } from '@/shared/lib/api'
import { useAuthStore } from '@/shared/store/auth'
import { DeleteRecordDialog } from './DeleteRecordDialog'
import { RecordCard } from './RecordCard'
import { RecordDialog } from './RecordDialog'
import { RecordGridCard } from './RecordGridCard'
import {
  buildTimelineQuery,
  EMPTY_TIMELINE_FILTER,
  isFilterActive,
  MIN_RATING_OPTIONS,
  toggleMoodFilter,
} from './timeline-filter'
import { STATUS_LABEL, type RecordPage, type RecordResponse, type RecordStatus } from './types'
import { useCuratedMoods } from './use-curated-moods'
import { readViewMode, writeViewMode, type ViewMode } from './view-mode'
import { ViewModeToggle } from './ViewModeToggle'

const STATUSES: RecordStatus[] = ['WANT', 'DOING', 'DONE', 'DROPPED']

/** F-04-01·02 — 내 타임라인. 커서 기반 무한 스크롤과 필터. */
export function TimelineView() {
  // 필드별 구독 — 객체 셀렉터는 zustand v5 에서 무한 렌더를 유발한다
  const user = useAuthStore((state) => state.user)
  const ready = useAuthStore((state) => state.ready)

  const moodOptions = useCuratedMoods()

  const [items, setItems] = useState<RecordPage['items']>([])
  const [cursor, setCursor] = useState<string | null>(null)
  const [total, setTotal] = useState(0)
  // 필터는 객체 하나로 들고 있는다 — 무엇이 바뀌든 새 객체가 되어 처음부터 다시 읽는다
  const [filter, setFilter] = useState(EMPTY_TIMELINE_FILTER)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  // 서버 렌더링에는 localStorage 가 없다. 리스트로 그린 뒤 저장된 선택으로 교정한다 —
  // 렌더 중에 읽으면 하이드레이션이 어긋난다.
  const [viewMode, setViewMode] = useState<ViewMode>('list')

  useEffect(() => {
    setViewMode(readViewMode())
  }, [])

  const changeViewMode = (next: ViewMode) => {
    setViewMode(next)
    writeViewMode(next)
  }
  // 수정·삭제 다이얼로그는 목록이 소유한다 — 카드가 들고 있으면 갱신을 위로 올리기 어렵다
  const [editing, setEditing] = useState<RecordResponse | null>(null)
  const [deleting, setDeleting] = useState<RecordResponse | null>(null)

  const load = useCallback(
    async (nextCursor: string | null, append: boolean) => {
      setLoading(true)
      setError(null)
      try {
        const query = buildTimelineQuery(filter, nextCursor)
        const page = await apiGet<RecordPage>(`/api/v1/records/me${query ? `?${query}` : ''}`)
        setItems((prev) => (append ? [...prev, ...page.items] : page.items))
        setCursor(page.nextCursor)
        setTotal(page.totalCount)
      } catch (e) {
        setError(e instanceof ApiError ? e.problem.title : '기록을 불러오지 못했습니다')
      } finally {
        setLoading(false)
      }
    },
    [filter],
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
          <FilterChip
            selected={filter.type === null}
            onClick={() => setFilter((f) => ({ ...f, type: null }))}
          >
            전체
          </FilterChip>
          {CONTENT_TYPES.map((type) => (
            <FilterChip
              key={type}
              selected={filter.type === type}
              onClick={() => setFilter((f) => ({ ...f, type: f.type === type ? null : type }))}
            >
              {CONTENT_TYPE_LABEL[type]}
            </FilterChip>
          ))}
        </div>
        <div className={flex({ gap: '1.5', flexWrap: 'wrap' })}>
          {STATUSES.map((status) => (
            <FilterChip
              key={status}
              selected={filter.status === status}
              onClick={() =>
                setFilter((f) => ({ ...f, status: f.status === status ? null : status }))
              }
            >
              {STATUS_LABEL[status]}
            </FilterChip>
          ))}
        </div>
      </div>

      {/* 무드는 이 서비스의 1급 개념이다 — 남의 무드는 /moods 로 탐색되는데
          정작 내 기록을 무드로 거를 수 없었다 (F-04-02) */}
      {moodOptions.length > 0 && (
        <div className={flex({ gap: '1.5', flexWrap: 'wrap' })}>
          {moodOptions.map((mood) => {
            const selected = filter.moods.includes(mood.name)
            return (
              <button
                key={mood.name}
                type="button"
                // 서버가 대조하는 것은 정규화된 name 이다 — displayName 을 보내면 조용히 0건이 된다
                onClick={() => setFilter((f) => toggleMoodFilter(f, mood.name))}
                aria-pressed={selected}
                style={
                  selected
                    ? { backgroundColor: mood.color ?? FALLBACK_MOOD_COLOR, color: '#fff' }
                    : { borderColor: mood.color ?? FALLBACK_MOOD_COLOR }
                }
                className={moodChipStyle}
              >
                {mood.displayName}
              </button>
            )
          })}
        </div>
      )}

      <div className={flex({ gap: '2', alignItems: 'center', flexWrap: 'wrap' })}>
        <label className={flex({ gap: '2', alignItems: 'center' })}>
          <span className={css({ textStyle: 'caption', color: 'fg.muted' })}>별점</span>
          <select
            value={filter.minRating ?? ''}
            onChange={(event) =>
              setFilter((f) => ({
                ...f,
                minRating: event.target.value === '' ? null : Number(event.target.value),
              }))
            }
            className={filterControlStyle}
          >
            <option value="">전체</option>
            {MIN_RATING_OPTIONS.map((value) => (
              <option key={value} value={value}>
                {value.toFixed(1)} 이상
              </option>
            ))}
          </select>
        </label>

        {/* 서버는 created_at 으로 거른다 — "본 날"이 아니라 "기록한 날"이다.
            기준을 바꾸는 것은 별도 이슈로 뒀다 (#22 의 열린 질문) */}
        <span className={css({ textStyle: 'caption', color: 'fg.muted' })}>기록한 날</span>
        <input
          type="date"
          value={filter.from}
          max={filter.to || undefined}
          onChange={(event) => setFilter((f) => ({ ...f, from: event.target.value }))}
          aria-label="기록한 날 — 시작"
          className={filterControlStyle}
        />
        <span className={css({ textStyle: 'caption', color: 'fg.muted' })}>—</span>
        <input
          type="date"
          value={filter.to}
          min={filter.from || undefined}
          onChange={(event) => setFilter((f) => ({ ...f, to: event.target.value }))}
          aria-label="기록한 날 — 끝"
          className={filterControlStyle}
        />
      </div>

      {/* 별점은 선택 사항이다 (F-03-03). 이 서비스에서는 무드만 남긴 기록이 흔하므로
          "3.5 이상"을 고르면 그것들이 통째로 빠진다는 사실을 감추지 않는다 */}
      {filter.minRating !== null && (
        <p className={css({ textStyle: 'caption', color: 'fg.muted' })}>
          별점을 남기지 않은 기록은 제외됩니다.
        </p>
      )}

      <div className={flex({ gap: '3', alignItems: 'center', flexWrap: 'wrap' })}>
        <p className={css({ textStyle: 'caption', color: 'fg.muted' })}>{total}개의 기록</p>
        <div className={css({ ml: 'auto' })}>
          <ViewModeToggle mode={viewMode} onChange={changeViewMode} />
        </div>
        {isFilterActive(filter) && (
          <button
            type="button"
            onClick={() => setFilter(EMPTY_TIMELINE_FILTER)}
            className={css({
              textStyle: 'caption',
              color: 'fg.muted',
              cursor: 'pointer',
              bg: 'transparent',
              textDecoration: 'underline',
            })}
          >
            필터 초기화
          </button>
        )}
      </div>

      {error && (
        <p role="alert" className={css({ textStyle: 'body', color: 'danger.500' })}>
          {error}
        </p>
      )}

      {!loading && items.length === 0 && (
        <div className={stack({ gap: '3', alignItems: 'flex-start', py: '8' })}>
          {/* 필터 때문에 빈 것을 "기록이 없다"고 말하면 사용자는 기록이 사라진 줄 안다 */}
          {isFilterActive(filter) ? (
            <>
              <p className={css({ textStyle: 'body', color: 'fg.muted' })}>
                조건에 맞는 기록이 없습니다.
              </p>
              <button
                type="button"
                onClick={() => setFilter(EMPTY_TIMELINE_FILTER)}
                className={css({
                  textStyle: 'body',
                  px: '4',
                  py: '2',
                  rounded: 'md',
                  cursor: 'pointer',
                  bg: 'bg.subtle',
                  color: 'fg.default',
                })}
              >
                필터 초기화
              </button>
            </>
          ) : (
            <>
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
            </>
          )}
        </div>
      )}

      {/* Panda 는 빌드 타임 추출기다 — 클래스를 문자열로 조립하지 말고 두 레이아웃을
          각각 정적으로 정의해 고른다 */}
      {viewMode === 'grid' ? (
        <div
          className={grid({
            columns: { base: 2, sm: 3, md: 4 },
            gap: '4',
          })}
        >
          {items.map((record) => (
            <RecordGridCard
              key={record.id}
              record={record}
              own
              onEdit={() => setEditing(record)}
              onDelete={() => setDeleting(record)}
            />
          ))}
        </div>
      ) : (
        <div className={stack({ gap: '3' })}>
          {items.map((record) => (
            <RecordCard
              key={record.id}
              record={record}
              own
              onEdit={() => setEditing(record)}
              onDelete={() => setDeleting(record)}
            />
          ))}
        </div>
      )}

      {editing && (
        <RecordDialog
          record={editing}
          onClose={() => setEditing(null)}
          onUpdated={(updated) =>
            // 목록 전체를 다시 읽으면 스크롤 위치와 커서가 날아간다. 그 항목만 갈아끼운다.
            setItems((prev) => prev.map((item) => (item.id === updated.id ? updated : item)))
          }
        />
      )}

      {deleting && (
        <DeleteRecordDialog
          record={deleting}
          onClose={() => setDeleting(null)}
          onDeleted={(recordId) => {
            setItems((prev) => prev.filter((item) => item.id !== recordId))
            setTotal((prev) => Math.max(0, prev - 1))
          }}
        />
      )}

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

const filterControlStyle = css({
  px: '2',
  py: '1',
  rounded: 'md',
  textStyle: 'caption',
  bg: 'bg.surface',
  color: 'fg.default',
  borderWidth: '1px',
  borderStyle: 'solid',
  borderColor: 'border.default',
})

const moodChipStyle = css({
  textStyle: 'caption',
  px: '3',
  py: '1.5',
  rounded: 'full',
  cursor: 'pointer',
  borderWidth: '1px',
  borderStyle: 'solid',
  bg: 'bg.surface',
  color: 'fg.default',
})
