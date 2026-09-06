'use client'

import { useEffect, useState } from 'react'
import { css } from 'styled-system/css'
import { flex, grid, stack } from 'styled-system/patterns'
import { ApiError } from '@litmood/api-client'
import { RecordCard } from '@/features/record/RecordCard'
import { RecordGridCard } from '@/features/record/RecordGridCard'
import type { RecordPage, RecordResponse } from '@/features/record/types'
import { readViewMode, writeViewMode, type ViewMode } from '@/features/record/view-mode'
import { ViewModeToggle } from '@/features/record/ViewModeToggle'
import { apiGet } from '@/shared/lib/api'
import { useAuthStore } from '@/shared/store/auth'

/**
 * 공개 프로필의 기록 목록.
 *
 * 서버가 그린 공개 목록을 그대로 쓰다가, 로그인한 사용자에게는
 * 자신의 세션으로 다시 조회해 교체한다. 팔로워라면 FOLLOWERS 기록이,
 * 본인이라면 비공개 기록까지 포함된 목록이 온다 (F-03-07).
 *
 * 이렇게 나눈 이유: 프로필 페이지는 검색 유입 경로라 캐시된 서버 렌더링이
 * 필요한데(NFR-06), 캐시된 응답에 조회자별 내용을 담을 수는 없다.
 *
 * 재조회는 목록을 <b>덧붙이기만</b> 하는 것이 아니라 <b>비우기도</b> 한다.
 * 차단 관계면 서버가 404 로 응답하는데, 그때 서버가 그려 둔 공개 목록을 그대로
 * 두면 차단하고도 상대 기록이 계속 보인다 (이슈 #17).
 */
export function ProfileRecords({
  handle,
  initialRecords,
  initialTotal,
}: {
  handle: string
  initialRecords: RecordResponse[]
  initialTotal: number
}) {
  const user = useAuthStore((state) => state.user)
  const ready = useAuthStore((state) => state.ready)
  const [records, setRecords] = useState(initialRecords)
  const [total, setTotal] = useState(initialTotal)
  const [blocked, setBlocked] = useState(false)
  // 타임라인과 같은 선택을 쓴다 — 한 사람의 취향이 화면마다 다를 이유가 없다
  const [viewMode, setViewMode] = useState<ViewMode>('list')

  useEffect(() => {
    setViewMode(readViewMode())
  }, [])

  const changeViewMode = (next: ViewMode) => {
    setViewMode(next)
    writeViewMode(next)
  }

  useEffect(() => {
    if (!ready || !user) return

    apiGet<RecordPage>(`/api/v1/users/@${encodeURIComponent(handle)}/records?limit=20`)
      .then((page) => {
        setRecords(page.items)
        setTotal(page.totalCount)
      })
      .catch((error: unknown) => {
        // 페이지가 이미 그려진 이상 사용자는 존재한다. 여기서의 404 는 차단 관계라는 뜻이다
        // (서버는 존재를 숨기려 403 대신 404 로 응답한다).
        if (error instanceof ApiError && error.problem.status === 404) {
          setBlocked(true)
          setRecords([])
          setTotal(0)
          return
        }
        // 그 밖의 실패는 서버가 그린 공개 목록을 유지한다
      })
  }, [ready, user, handle])

  return (
    <div className={stack({ gap: '3' })}>
      {/* 개수와 목록은 반드시 같은 출처에서 나와야 한다.
          제목을 서버 값으로, 목록을 클라이언트 값으로 두면 "기록 2"인데
          3개가 보이는 어긋남이 생긴다. */}
      <div className={flex({ gap: '3', alignItems: 'center', flexWrap: 'wrap' })}>
        <h2 className={css({ textStyle: 'title' })}>
          기록 <span className={css({ color: 'fg.muted' })}>{total}</span>
        </h2>
        {records.length > 0 && (
          <div className={css({ ml: 'auto' })}>
            <ViewModeToggle mode={viewMode} onChange={changeViewMode} />
          </div>
        )}
      </div>

      {blocked ? (
        <p className={css({ textStyle: 'body', color: 'fg.muted' })}>
          차단한 사용자입니다. 설정에서 차단을 해제하면 다시 보입니다.
        </p>
      ) : records.length === 0 ? (
        <p className={css({ textStyle: 'body', color: 'fg.muted' })}>
          아직 공개된 기록이 없습니다.
        </p>
      ) : (
        <RecordList records={records} own={user?.handle === handle} mode={viewMode} />
      )}
    </div>
  )
}

/** 목록은 두 레이아웃을 각각 정적으로 정의해 고른다 (Panda 는 빌드 타임 추출기다). */
function RecordList({
  records,
  own,
  mode,
}: {
  records: RecordResponse[]
  own: boolean
  mode: ViewMode
}) {
  if (mode === 'grid') {
    return (
      <div className={grid({ columns: { base: 2, sm: 3, md: 4 }, gap: '4' })}>
        {records.map((record) => (
          <RecordGridCard key={record.id} record={record} own={own} />
        ))}
      </div>
    )
  }
  return (
    <div className={stack({ gap: '3' })}>
      {records.map((record) => (
        <RecordCard key={record.id} record={record} own={own} />
      ))}
    </div>
  )
}
