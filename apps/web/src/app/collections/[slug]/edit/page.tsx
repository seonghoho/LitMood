import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import { css } from 'styled-system/css'
import { stack } from 'styled-system/patterns'
import { CollectionEditor } from '@/features/collection/CollectionEditor'
import type { CollectionResponse } from '@/features/collection/types'
import { decodeRouteParam } from '@/shared/lib/route-params'

export const metadata: Metadata = { title: '컬렉션 편집', robots: { index: false } }

const API_BASE = process.env.API_INTERNAL_BASE_URL ?? 'http://localhost:8080'

export default async function EditCollectionPage({
  params,
}: {
  params: Promise<{ slug: string }>
}) {
  const slug = decodeRouteParam((await params).slug)
  // 편집 화면은 항상 최신 상태여야 하므로 캐시하지 않는다
  const response = await fetch(`${API_BASE}/api/v1/collections/${encodeURIComponent(slug)}`, {
    cache: 'no-store',
  })
  if (!response.ok) notFound()

  const collection = (await response.json()) as CollectionResponse

  return (
    <main className={stack({ gap: '6', maxW: '2xl', mx: 'auto', px: '6', py: '12' })}>
      <div className={stack({ gap: '1' })}>
        <p className={css({ textStyle: 'caption', color: 'fg.muted' })}>컬렉션 편집</p>
        <h1 className={css({ textStyle: 'title' })}>{collection.title}</h1>
      </div>
      <CollectionEditor initial={collection} />
    </main>
  )
}
