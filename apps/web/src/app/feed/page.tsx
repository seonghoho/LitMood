import type { Metadata } from 'next'
import { css } from 'styled-system/css'
import { stack } from 'styled-system/patterns'
import { FeedView } from '@/features/social/FeedView'

export const metadata: Metadata = { title: '피드', robots: { index: false } }

export default function FeedPage() {
  return (
    <main className={stack({ gap: '6', maxW: '2xl', mx: 'auto', px: '6', py: '12' })}>
      <h1 className={css({ textStyle: 'title' })}>피드</h1>
      <FeedView />
    </main>
  )
}
