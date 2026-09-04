'use client'

import { useEffect, useRef, useState } from 'react'
import { css } from 'styled-system/css'
import { flex, stack } from 'styled-system/patterns'
import { ApiError } from '@litmood/api-client'
import { apiDelete } from '@/shared/lib/api'
import type { RecordResponse } from './types'

/**
 * 삭제 확인 (이슈 #5).
 *
 * 서버는 soft delete 지만 사용자에게 되돌릴 수단이 없으므로 영구 삭제처럼 안내한다.
 * 지운 콘텐츠는 다시 기록할 수 있다는 점은 알려준다 — 불변식 1 때문에
 * "이미 기록함" 으로 막힐까 걱정하는 사람이 있다.
 */
export function DeleteRecordDialog({
  record,
  onClose,
  onDeleted,
}: {
  record: RecordResponse
  onClose: () => void
  onDeleted: (recordId: number) => void
}) {
  const [deleting, setDeleting] = useState(false)
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

  const remove = async () => {
    setDeleting(true)
    setError(null)
    try {
      await apiDelete(`/api/v1/records/${record.id}`)
      onDeleted(record.id)
      onClose()
    } catch (e) {
      setError(e instanceof ApiError ? e.problem.title : '기록을 삭제하지 못했습니다')
    } finally {
      setDeleting(false)
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
        role="alertdialog"
        aria-modal="true"
        aria-label={`${record.content.title} 기록 삭제`}
        tabIndex={-1}
        className={stack({
          gap: '4',
          w: 'full',
          maxW: '400px',
          bg: 'bg.surface',
          p: '5',
          roundedTop: 'lg',
          roundedBottom: { base: '0', md: 'lg' },
        })}
      >
        <div className={stack({ gap: '2' })}>
          <h2 className={css({ textStyle: 'title', color: 'fg.default' })}>기록을 삭제할까요?</h2>
          <p className={css({ textStyle: 'body', color: 'fg.default' })}>{record.content.title}</p>
          <p className={css({ textStyle: 'caption', color: 'fg.muted', lineHeight: '1.6' })}>
            되돌릴 수 없습니다. 남긴 별점·무드·한줄평이 함께 사라집니다. 같은 콘텐츠는 나중에 다시
            기록할 수 있습니다.
          </p>
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
            onClick={() => void remove()}
            disabled={deleting}
            className={css({
              textStyle: 'body',
              fontWeight: '600',
              px: '5',
              py: '2',
              rounded: 'md',
              cursor: 'pointer',
              bg: 'danger.500',
              color: 'white',
              _disabled: { opacity: 0.6, cursor: 'not-allowed' },
            })}
          >
            {deleting ? '삭제 중…' : '삭제'}
          </button>
        </div>
      </div>
    </div>
  )
}
