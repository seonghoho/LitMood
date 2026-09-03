import 'server-only'
import { decodeRouteParam } from '@/shared/lib/route-params'
import type { CollectionResponse } from './types'

/**
 * 서버 컴포넌트·OG 이미지에서 쓰는 조회 함수.
 *
 * 페이지 파일은 Next 가 정한 export 만 허용하므로(default, metadata 등)
 * 공용 fetch 는 여기에 둔다.
 */

const API_BASE = process.env.API_INTERNAL_BASE_URL ?? 'http://localhost:8080'
const REVALIDATE_SECONDS = 60

export async function fetchCollection(rawSlug: string): Promise<CollectionResponse | null> {
  const slug = decodeRouteParam(rawSlug)
  const response = await fetch(`${API_BASE}/api/v1/collections/${encodeURIComponent(slug)}`, {
    next: { revalidate: REVALIDATE_SECONDS, tags: [`collection:${slug}`] },
  })
  return response.ok ? ((await response.json()) as CollectionResponse) : null
}
