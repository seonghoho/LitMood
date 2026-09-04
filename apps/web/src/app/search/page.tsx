import type { Metadata } from 'next'
import { css } from 'styled-system/css'
import { stack } from 'styled-system/patterns'
import { SearchView } from '@/features/content/SearchView'

export const metadata: Metadata = {
  title: '검색',
  description: '책·영화·음악을 한 번에 검색합니다',
}

export default function SearchPage() {
  return (
    <main className={stack({ gap: '6', maxW: '2xl', mx: 'auto', px: '6', py: '12' })}>
      <h1 className={css({ textStyle: 'title' })}>검색</h1>
      <SearchView />
    </main>
  )
}
