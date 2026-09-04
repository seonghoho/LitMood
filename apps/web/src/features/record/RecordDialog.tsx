'use client'

import { useEffect, useRef, useState } from 'react'
import { css } from 'styled-system/css'
import { flex, stack } from 'styled-system/patterns'
import { ApiError } from '@litmood/api-client'
import { FALLBACK_MOOD_COLOR } from '@litmood/ui'
import type { ContentSummary } from '@/features/content/types'
import { apiGet, apiPatch, apiPost } from '@/shared/lib/api'
import { buildRecordPatch } from './record-edit'
import {
  allowsRating,
  STATUS_LABEL,
  VISIBILITY_LABEL,
  type MoodTag,
  type RecordResponse,
  type RecordStatus,
  type Visibility,
} from './types'

const STATUSES: RecordStatus[] = ['WANT', 'DOING', 'DONE', 'DROPPED']
const VISIBILITIES: Visibility[] = ['PUBLIC', 'FOLLOWERS', 'PRIVATE']
const RATINGS = [0.5, 1, 1.5, 2, 2.5, 3, 3.5, 4, 4.5, 5]
const MAX_MOODS = 5

/**
 * 기록 작성·수정 (F-03-01, 이슈 #5).
 *
 * 수용 기준: 검색 결과에서 <b>화면 전환 없이</b> 기록을 마칠 수 있어야 하고,
 * 필수 입력은 상태 하나뿐이어야 한다. 그래서 페이지가 아니라 다이얼로그다.
 *
 * <p>수정도 같은 화면을 쓴다. 만들 때와 고칠 때의 입력이 같은데 화면이 둘이면
 * 규칙(WANT 로 바꾸면 별점이 사라진다 등)을 양쪽에 따로 구현하게 된다.
 */
export function RecordDialog({
  content,
  record,
  onClose,
  onCreated,
  onUpdated,
}: {
  /** 생성 모드의 대상 콘텐츠 */
  content?: ContentSummary
  /** 넘기면 수정 모드가 된다 */
  record?: RecordResponse
  onClose: () => void
  onCreated?: (externalId: string) => void
  onUpdated?: (record: RecordResponse) => void
}) {
  const editing = record != null
  // 표시용 정보 — 수정 모드에서는 기록에 붙어 있는 콘텐츠 스냅샷을 쓴다
  const display = record ? record.content : content!

  const [status, setStatus] = useState<RecordStatus>(record?.status ?? 'DONE')
  const [rating, setRating] = useState<number | null>(record?.rating ?? null)
  const [moods, setMoods] = useState<string[]>(record?.moods.map((m) => m.displayName) ?? [])
  const [review, setReview] = useState(record?.review ?? '')
  const [visibility, setVisibility] = useState<Visibility>(record?.visibility ?? 'PUBLIC')
  const [isSpoiler, setIsSpoiler] = useState(record?.isSpoiler ?? false)
  const [contextNote, setContextNote] = useState(record?.contextNote ?? '')
  const [startedAt, setStartedAt] = useState(record?.startedAt ?? '')
  const [finishedAt, setFinishedAt] = useState(record?.finishedAt ?? '')
  const [repeatCount, setRepeatCount] = useState(record?.repeatCount ?? 0)
  // 기본은 접어둔다 — 필수 입력은 상태 하나뿐이어야 한다 (F-03-01 수용 기준).
  // 이미 값이 있는 기록을 열 때는 펼쳐야 무엇이 들어 있는지 보인다.
  const [showDetails, setShowDetails] = useState(
    Boolean(record?.contextNote || record?.startedAt || record?.finishedAt || record?.repeatCount),
  )
  const [curated, setCurated] = useState<MoodTag[]>([])
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const dialogRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    apiGet<MoodTag[]>('/api/v1/moods?limit=12')
      .then(setCurated)
      .catch(() => setCurated([]))
  }, [])

  // Esc 로 닫기 + 열려 있는 동안 배경 스크롤 차단
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKeyDown)
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    dialogRef.current?.focus()
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      document.body.style.overflow = previousOverflow
    }
  }, [onClose])

  // 불변식 2 — WANT 로 바꾸면 별점을 지운다. 서버가 거부하기 전에 UI 에서 막는다.
  const changeStatus = (next: RecordStatus) => {
    setStatus(next)
    if (!allowsRating(next)) setRating(null)
  }

  const toggleMood = (name: string) => {
    setMoods((current) =>
      current.includes(name)
        ? current.filter((m) => m !== name)
        : current.length >= MAX_MOODS
          ? current
          : [...current, name],
    )
  }

  const submit = async () => {
    setSubmitting(true)
    setError(null)
    try {
      if (record) {
        const patch = buildRecordPatch(record, {
          status,
          rating,
          moods,
          review,
          visibility,
          isSpoiler,
          contextNote,
          startedAt,
          finishedAt,
          repeatCount,
        })
        // 바뀐 것이 없으면 요청 자체를 보내지 않는다
        const updated = Object.keys(patch).length
          ? await apiPatch<RecordResponse>(`/api/v1/records/${record.id}`, patch)
          : record
        onUpdated?.(updated)
      } else {
        await apiPost('/api/v1/records', {
          provider: content!.provider,
          externalId: content!.externalId,
          status,
          rating,
          moods,
          review: review.trim() || null,
          visibility,
          isSpoiler,
          contextNote: contextNote.trim() || null,
          startedAt: startedAt || null,
          finishedAt: finishedAt || null,
          repeatCount,
        })
        onCreated?.(content!.externalId)
      }
      onClose()
    } catch (e) {
      setError(e instanceof ApiError ? e.problem.title : '기록을 저장하지 못했습니다')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose()
      }}
      className={flex({
        position: 'fixed',
        inset: '0',
        bg: 'rgba(0,0,0,0.45)',
        alignItems: { base: 'flex-end', md: 'center' },
        justifyContent: 'center',
        zIndex: '50',
        p: { base: '0', md: '4' },
      })}
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-label={`${display.title} ${editing ? '기록 수정' : '기록하기'}`}
        tabIndex={-1}
        className={stack({
          gap: '4',
          w: 'full',
          maxW: '480px',
          maxH: '90vh',
          overflowY: 'auto',
          bg: 'bg.surface',
          p: '5',
          roundedTop: 'lg',
          roundedBottom: { base: '0', md: 'lg' },
        })}
      >
        <header className={stack({ gap: '1' })}>
          <p className={css({ textStyle: 'caption', color: 'fg.muted' })}>
            {editing ? '기록 수정' : '기록하기'}
          </p>
          <h2 className={css({ textStyle: 'title', color: 'fg.default' })}>{display.title}</h2>
          {display.creators.length > 0 && (
            <p className={css({ textStyle: 'caption', color: 'fg.muted' })}>
              {display.creators.join(', ')}
            </p>
          )}
        </header>

        <Field label="상태" required>
          <div className={flex({ gap: '2', flexWrap: 'wrap' })}>
            {STATUSES.map((s) => (
              <Chip key={s} selected={s === status} onClick={() => changeStatus(s)}>
                {STATUS_LABEL[s]}
              </Chip>
            ))}
          </div>
        </Field>

        {/* 별점은 선택 사항이고, 아직 보지 않은 상태에서는 아예 노출하지 않는다 */}
        {allowsRating(status) && (
          <Field label="별점">
            <div className={flex({ gap: '1', flexWrap: 'wrap' })}>
              {RATINGS.map((value) => (
                <Chip
                  key={value}
                  selected={rating === value}
                  onClick={() => setRating(rating === value ? null : value)}
                >
                  {value.toFixed(1)}
                </Chip>
              ))}
            </div>
          </Field>
        )}

        <Field label={`무드 (${moods.length}/${MAX_MOODS})`}>
          <div className={flex({ gap: '2', flexWrap: 'wrap' })}>
            {curated.map((mood) => {
              const selected = moods.includes(mood.displayName)
              return (
                <button
                  key={mood.name}
                  type="button"
                  onClick={() => toggleMood(mood.displayName)}
                  aria-pressed={selected}
                  style={
                    selected
                      ? { backgroundColor: mood.color ?? FALLBACK_MOOD_COLOR, color: '#fff' }
                      : { borderColor: mood.color ?? FALLBACK_MOOD_COLOR }
                  }
                  className={css({
                    textStyle: 'caption',
                    px: '3',
                    py: '1.5',
                    rounded: 'full',
                    cursor: 'pointer',
                    borderWidth: '1px',
                    borderStyle: 'solid',
                    bg: 'bg.surface',
                    color: 'fg.default',
                  })}
                >
                  {mood.displayName}
                </button>
              )
            })}
          </div>
        </Field>

        <Field label="한줄평">
          <textarea
            value={review}
            onChange={(event) => setReview(event.target.value)}
            rows={3}
            maxLength={2000}
            placeholder="그때 어땠나요?"
            className={css({
              w: 'full',
              px: '3',
              py: '2',
              rounded: 'md',
              textStyle: 'body',
              resize: 'vertical',
              bg: 'bg.canvas',
              color: 'fg.default',
              borderWidth: '1px',
              borderStyle: 'solid',
              borderColor: 'border.default',
            })}
          />
        </Field>

        <Field label="공개 범위">
          <div className={flex({ gap: '2' })}>
            {VISIBILITIES.map((v) => (
              <Chip key={v} selected={v === visibility} onClick={() => setVisibility(v)}>
                {VISIBILITY_LABEL[v]}
              </Chip>
            ))}
          </div>
        </Field>

        <div className={stack({ gap: '3' })}>
          <button
            type="button"
            onClick={() => setShowDetails((open) => !open)}
            aria-expanded={showDetails}
            className={css({
              textStyle: 'caption',
              color: 'fg.muted',
              cursor: 'pointer',
              bg: 'transparent',
              textAlign: 'left',
              w: 'fit-content',
            })}
          >
            {showDetails ? '자세히 접기' : '자세히 — 언제·어디서, 스포일러'}
          </button>

          {showDetails && (
            <div className={stack({ gap: '4' })}>
              <Field label="언제">
                <div className={flex({ gap: '2', alignItems: 'center', flexWrap: 'wrap' })}>
                  <input
                    type="date"
                    value={startedAt}
                    max={finishedAt || undefined}
                    onChange={(event) => setStartedAt(event.target.value)}
                    aria-label="시작일"
                    className={dateStyle}
                  />
                  <span className={css({ textStyle: 'caption', color: 'fg.muted' })}>—</span>
                  <input
                    type="date"
                    value={finishedAt}
                    min={startedAt || undefined}
                    onChange={(event) => setFinishedAt(event.target.value)}
                    aria-label="종료일"
                    className={dateStyle}
                  />
                </div>
              </Field>

              <Field label="어디서">
                <input
                  type="text"
                  value={contextNote}
                  onChange={(event) => setContextNote(event.target.value)}
                  maxLength={200}
                  placeholder="지하철에서, 잠들기 전에…"
                  className={css({
                    w: 'full',
                    px: '3',
                    py: '2',
                    rounded: 'md',
                    textStyle: 'body',
                    bg: 'bg.canvas',
                    color: 'fg.default',
                    borderWidth: '1px',
                    borderStyle: 'solid',
                    borderColor: 'border.default',
                  })}
                />
              </Field>

              <Field label="다시 본 횟수">
                <div className={flex({ gap: '2', alignItems: 'center' })}>
                  <button
                    type="button"
                    onClick={() => setRepeatCount((n) => Math.max(0, n - 1))}
                    disabled={repeatCount === 0}
                    aria-label="다시 본 횟수 줄이기"
                    className={stepStyle}
                  >
                    −
                  </button>
                  <span className={css({ textStyle: 'body', color: 'fg.default', minW: '3ch' })}>
                    {repeatCount}회
                  </span>
                  <button
                    type="button"
                    onClick={() => setRepeatCount((n) => n + 1)}
                    className={css({
                      textStyle: 'caption',
                      px: '3',
                      py: '1.5',
                      rounded: 'full',
                      cursor: 'pointer',
                      borderWidth: '1px',
                      borderStyle: 'solid',
                      borderColor: 'border.default',
                      bg: 'bg.surface',
                      color: 'fg.default',
                    })}
                  >
                    다시 봄
                  </button>
                </div>
              </Field>

              <label className={flex({ gap: '2', alignItems: 'center', cursor: 'pointer' })}>
                <input
                  type="checkbox"
                  checked={isSpoiler}
                  onChange={(event) => setIsSpoiler(event.target.checked)}
                />
                <span className={css({ textStyle: 'body', color: 'fg.default' })}>
                  스포일러 포함
                </span>
                <span className={css({ textStyle: 'caption', color: 'fg.muted' })}>
                  다른 사람에게는 가려서 보입니다
                </span>
              </label>
            </div>
          )}
        </div>

        {error && (
          <p role="alert" className={css({ textStyle: 'caption', color: 'danger.500' })}>
            {error}
          </p>
        )}

        <div className={flex({ gap: '2', justifyContent: 'flex-end' })}>
          <button
            type="button"
            onClick={onClose}
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
            취소
          </button>
          <button
            type="button"
            onClick={submit}
            disabled={submitting}
            className={css({
              textStyle: 'body',
              fontWeight: '600',
              px: '5',
              py: '2',
              rounded: 'md',
              cursor: 'pointer',
              bg: 'brand.default',
              color: 'fg.onAccent',
              _disabled: { opacity: 0.6, cursor: 'not-allowed' },
            })}
          >
            {submitting ? '저장 중…' : editing ? '저장' : '기록하기'}
          </button>
        </div>
      </div>
    </div>
  )
}

function Field({
  label,
  required,
  children,
}: {
  label: string
  required?: boolean
  children: React.ReactNode
}) {
  return (
    <div className={stack({ gap: '2' })}>
      <span className={css({ textStyle: 'caption', color: 'fg.muted' })}>
        {label}
        {required && <span className={css({ color: 'brand.default' })}> *</span>}
      </span>
      {children}
    </div>
  )
}

function Chip({
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

const dateStyle = css({
  px: '3',
  py: '2',
  rounded: 'md',
  textStyle: 'body',
  bg: 'bg.canvas',
  color: 'fg.default',
  borderWidth: '1px',
  borderStyle: 'solid',
  borderColor: 'border.default',
})

const stepStyle = css({
  textStyle: 'body',
  w: '32px',
  h: '32px',
  rounded: 'full',
  cursor: 'pointer',
  borderWidth: '1px',
  borderStyle: 'solid',
  borderColor: 'border.default',
  bg: 'bg.surface',
  color: 'fg.default',
  _disabled: { opacity: 0.4, cursor: 'not-allowed' },
})
