'use client'

import { useEffect, useRef, useState } from 'react'
import { css } from 'styled-system/css'
import { flex, stack } from 'styled-system/patterns'
import { ApiError } from '@litmood/api-client'
import { FALLBACK_MOOD_COLOR } from '@litmood/ui'
import type { ContentSummary } from '@/features/content/types'
import { apiGet, apiPost } from '@/shared/lib/api'
import {
  allowsRating,
  STATUS_LABEL,
  VISIBILITY_LABEL,
  type MoodTag,
  type RecordStatus,
  type Visibility,
} from './types'

const STATUSES: RecordStatus[] = ['WANT', 'DOING', 'DONE', 'DROPPED']
const VISIBILITIES: Visibility[] = ['PUBLIC', 'FOLLOWERS', 'PRIVATE']
const RATINGS = [0.5, 1, 1.5, 2, 2.5, 3, 3.5, 4, 4.5, 5]
const MAX_MOODS = 5

/**
 * 기록 작성 (F-03-01).
 *
 * 수용 기준: 검색 결과에서 <b>화면 전환 없이</b> 기록을 마칠 수 있어야 하고,
 * 필수 입력은 상태 하나뿐이어야 한다. 그래서 페이지가 아니라 다이얼로그다.
 */
export function RecordDialog({
  content,
  onClose,
  onCreated,
}: {
  content: ContentSummary
  onClose: () => void
  onCreated: (externalId: string) => void
}) {
  const [status, setStatus] = useState<RecordStatus>('DONE')
  const [rating, setRating] = useState<number | null>(null)
  const [moods, setMoods] = useState<string[]>([])
  const [review, setReview] = useState('')
  const [visibility, setVisibility] = useState<Visibility>('PUBLIC')
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
      await apiPost('/api/v1/records', {
        provider: content.provider,
        externalId: content.externalId,
        status,
        rating,
        moods,
        review: review.trim() || null,
        visibility,
      })
      onCreated(content.externalId)
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
        aria-label={`${content.title} 기록하기`}
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
          <p className={css({ textStyle: 'caption', color: 'fg.muted' })}>기록하기</p>
          <h2 className={css({ textStyle: 'title', color: 'fg.default' })}>{content.title}</h2>
          {content.creators.length > 0 && (
            <p className={css({ textStyle: 'caption', color: 'fg.muted' })}>
              {content.creators.join(', ')}
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
            {submitting ? '저장 중…' : '기록하기'}
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
