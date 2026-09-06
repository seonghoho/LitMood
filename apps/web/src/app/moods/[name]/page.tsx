import type { Metadata } from 'next'
import Link from 'next/link'
import { css } from 'styled-system/css'
import { stack } from 'styled-system/patterns'
import { FALLBACK_MOOD_COLOR } from '@litmood/ui'
import { RankedContentList } from '@/features/content/RankedContentList'
import type { ContentRef, MoodTag } from '@/features/record/types'
import { decodeRouteParam } from '@/shared/lib/route-params'

/**
 * 무드별 탐색 (F-07-01).
 *
 * "#새벽 을 쓴 사람들은 무엇을 봤는가" — 무드를 1급 개념으로 삼은 이 서비스의
 * 차별점이 실제로 값을 하는 화면이다. 검색 유입 대상이므로 서버 렌더링한다.
 */

const API_BASE = process.env.API_INTERNAL_BASE_URL ?? 'http://localhost:8080'

interface RankedContent {
  content: ContentRef
  recordCount: number
  averageRating: number | null
}

interface MoodDiscovery {
  mood: MoodTag
  contents: RankedContent[]
}

async function fetchDiscovery(name: string): Promise<MoodDiscovery> {
  const response = await fetch(
    `${API_BASE}/api/v1/moods/${encodeURIComponent(name)}/contents?limit=20`,
    { next: { revalidate: 300, tags: [`mood:${name}`] } },
  )
  return response.ok
    ? ((await response.json()) as MoodDiscovery)
    : { mood: { name, displayName: name, color: null, curated: false }, contents: [] }
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ name: string }>
}): Promise<Metadata> {
  const name = decodeRouteParam((await params).name)
  const title = `#${name}`
  const description = `${name} 기분으로 기록된 책·영화·음악`

  return {
    title,
    description,
    openGraph: { title, description, type: 'website' },
  }
}

export default async function MoodPage({ params }: { params: Promise<{ name: string }> }) {
  const name = decodeRouteParam((await params).name)
  const { mood, contents } = await fetchDiscovery(name)
  const color = mood.color ?? FALLBACK_MOOD_COLOR

  return (
    <main className={stack({ gap: '6', maxW: '2xl', mx: 'auto', px: '6', py: '12' })}>
      <header className={stack({ gap: '2' })}>
        <span
          style={{ backgroundColor: color }}
          className={css({
            textStyle: 'caption',
            px: '3',
            py: '1.5',
            rounded: 'full',
            color: 'white',
            alignSelf: 'flex-start',
          })}
        >
          {mood.displayName}
        </span>
        <h1 className={css({ textStyle: 'display', color: 'fg.default' })}>
          이 기분으로 기록된 것들
        </h1>
      </header>

      {contents.length === 0 ? (
        <p className={css({ textStyle: 'body', color: 'fg.muted' })}>
          아직 이 무드로 공개된 기록이 없습니다.
        </p>
      ) : (
        <RankedContentList items={contents} />
      )}

      <Link
        href="/search"
        className={css({ textStyle: 'caption', color: 'fg.muted', textDecoration: 'underline' })}
      >
        직접 기록하러 가기 →
      </Link>
    </main>
  )
}
