'use client'

import { useEffect, useRef, useState } from 'react'
import { css } from 'styled-system/css'
import { flex, stack } from 'styled-system/patterns'
import { ApiError, type ReportReason } from '@litmood/api-client'
import { apiPost } from '@/shared/lib/api'
import { REPORT_REASON_LABEL, REPORT_REASONS, reportPath, type ReportTarget } from './report'

/**
 * 신고 (F-06-05).
 *
 * 접수만 알리고 처리 결과는 약속하지 않는다 — 알려줄 수단이 없다.
 * 같은 대상을 다시 신고해도 서버가 한 번만 접수하므로 사용자는 실패를 보지 않는다.
 */
export function ReportDialog({ target, onClose }: { target: ReportTarget; onClose: () => void }) {
  const [reason, setReason] = useState<ReportReason>('SPAM')
  const [detail, setDetail] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [done, setDone] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const dialogRef = useRef<HTMLDivElement>(null)

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

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await apiPost(reportPath(target), { reason, detail: detail.trim() || null })
      setDone(true)
    } catch (e) {
      setError(e instanceof ApiError ? e.problem.title : '신고를 접수하지 못했습니다')
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
        aria-label={`${target.label} 신고`}
        tabIndex={-1}
        className={stack({
          gap: '4',
          w: 'full',
          maxW: '420px',
          bg: 'bg.surface',
          p: '5',
          roundedTop: 'lg',
          roundedBottom: { base: '0', md: 'lg' },
        })}
      >
        {done ? (
          <>
            <div className={stack({ gap: '2' })}>
              <h2 className={css({ textStyle: 'title', color: 'fg.default' })}>접수되었습니다</h2>
              <p className={css({ textStyle: 'caption', color: 'fg.muted', lineHeight: '1.6' })}>
                신고 내용은 운영자가 확인합니다. 처리 결과를 따로 알려드리지는 않습니다. 이 사용자의
                글을 보고 싶지 않다면 차단을 함께 이용하세요.
              </p>
            </div>
            <div className={flex({ justifyContent: 'flex-end' })}>
              <button type="button" onClick={onClose} className={primaryStyle}>
                닫기
              </button>
            </div>
          </>
        ) : (
          <form onSubmit={submit} className={stack({ gap: '4' })}>
            <div className={stack({ gap: '1' })}>
              <h2 className={css({ textStyle: 'title', color: 'fg.default' })}>신고하기</h2>
              <p className={css({ textStyle: 'caption', color: 'fg.muted' })}>{target.label}</p>
            </div>

            <fieldset className={stack({ gap: '2' })}>
              <legend className={css({ textStyle: 'caption', color: 'fg.muted', pb: '1' })}>
                사유
              </legend>
              {REPORT_REASONS.map((value) => (
                <label key={value} className={flex({ gap: '2', alignItems: 'center' })}>
                  <input
                    type="radio"
                    name="reason"
                    value={value}
                    checked={reason === value}
                    onChange={() => setReason(value)}
                  />
                  <span className={css({ textStyle: 'body', color: 'fg.default' })}>
                    {REPORT_REASON_LABEL[value]}
                  </span>
                </label>
              ))}
            </fieldset>

            <label className={stack({ gap: '1.5' })}>
              <span className={css({ textStyle: 'caption', color: 'fg.muted' })}>
                자세히 (선택) {detail.length}/500
              </span>
              <textarea
                value={detail}
                onChange={(event) => setDetail(event.target.value)}
                rows={3}
                maxLength={500}
                placeholder="어떤 점이 문제인지 적어 주시면 확인에 도움이 됩니다"
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
            </label>

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
              <button type="submit" disabled={submitting} className={primaryStyle}>
                {submitting ? '접수하는 중…' : '신고'}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}

const primaryStyle = css({
  textStyle: 'body',
  fontWeight: '600',
  px: '5',
  py: '2',
  rounded: 'md',
  cursor: 'pointer',
  bg: 'brand.default',
  color: 'fg.onAccent',
  _disabled: { opacity: 0.6 },
})
