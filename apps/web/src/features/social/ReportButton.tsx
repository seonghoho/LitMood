'use client'

import { useState } from 'react'
import { css } from 'styled-system/css'
import { useAuthStore } from '@/shared/store/auth'
import type { ReportTarget } from './report'
import { ReportDialog } from './ReportDialog'

/**
 * 신고 진입점 (F-06-05).
 *
 * 남의 것에만 보인다. 자기 것을 신고하면 서버가 거부하므로 버튼부터 감춘다.
 * 소유 여부는 호출부가 판단한다 — 화면마다 아는 방식이 다르다.
 */
export function ReportButton({
  target,
  ownerHandle,
}: {
  target: ReportTarget
  ownerHandle?: string
}) {
  const user = useAuthStore((state) => state.user)
  const ready = useAuthStore((state) => state.ready)
  const [open, setOpen] = useState(false)

  // 비로그인 조회자에게는 신고할 수단이 없다 (서버가 인증을 요구한다)
  if (!ready || !user) return null
  if (ownerHandle && user.handle === ownerHandle) return null

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className={css({
          textStyle: 'caption',
          px: '2',
          py: '1',
          rounded: 'md',
          cursor: 'pointer',
          bg: 'transparent',
          color: 'fg.muted',
        })}
      >
        신고
      </button>
      {open && <ReportDialog target={target} onClose={() => setOpen(false)} />}
    </>
  )
}
