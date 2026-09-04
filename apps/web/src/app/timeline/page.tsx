import type { Metadata } from 'next'
import { css } from 'styled-system/css'
import { stack } from 'styled-system/patterns'
import { TimelineView } from '@/features/record/TimelineView'

export const metadata: Metadata = { title: '내 기록' }

export default function TimelinePage() {
  return (
    <main className={stack({ gap: '6', maxW: '2xl', mx: 'auto', px: '6', py: '12' })}>
      <h1 className={css({ textStyle: 'title' })}>내 기록</h1>
      <TimelineView />
    </main>
  )
}
