'use client'

import { useEffect, useState } from 'react'
import { apiGet } from '@/shared/lib/api'
import type { MoodTag } from './types'

/**
 * 무드 선택지. 기록 다이얼로그와 타임라인 필터가 같은 목록을 쓴다.
 *
 * 순서는 서버가 정한다 — 큐레이션 무드가 앞에 오고 그다음이 사용량순이다.
 * 실패해도 화면을 막지 않는다. 무드 목록이 비면 칩이 안 보일 뿐,
 * 기록 작성도 조회도 정상 동작한다.
 */
export function useCuratedMoods(limit = 12): MoodTag[] {
  const [moods, setMoods] = useState<MoodTag[]>([])

  useEffect(() => {
    let alive = true
    apiGet<MoodTag[]>(`/api/v1/moods?limit=${limit}`)
      .then((list) => {
        if (alive) setMoods(list)
      })
      .catch(() => {
        if (alive) setMoods([])
      })
    return () => {
      alive = false
    }
  }, [limit])

  return moods
}
