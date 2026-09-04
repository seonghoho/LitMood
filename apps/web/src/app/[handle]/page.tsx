import type { Metadata } from 'next'
import Link from 'next/link'
import { notFound } from 'next/navigation'
import { css } from 'styled-system/css'
import { stack } from 'styled-system/patterns'
import type { CollectionSummary } from '@/features/collection/types'
import type { RecordPage } from '@/features/record/types'
import { FollowButton } from '@/features/social/FollowButton'
import { ProfileRecords } from '@/features/social/ProfileRecords'
import { decodeRouteParam } from '@/shared/lib/route-params'

/**
 * 공개 프로필 (NFR-06).
 *
 * 이 페이지가 Next.js 를 고른 이유다(ADR-001) — 검색 유입 경로이므로
 * 서버에서 렌더링되어야 하고, 공유 시 미리보기 카드가 필요하다.
 * 서버 컴포넌트에서 백엔드를 직접 호출한다.
 */

const API_BASE = process.env.API_INTERNAL_BASE_URL ?? 'http://localhost:8080'
// 프로필은 자주 바뀌지 않는다. 60초 캐시로 원본 부하를 줄인다.
const REVALIDATE_SECONDS = 60

interface PublicProfile {
  handle: string
  nickname: string
  bio: string | null
  avatarUrl: string | null
  followers: number
  following: number
  followedByMe: boolean
}

/** 라우트가 /@handle 형태이므로 URL 의 '@' 를 벗겨 API 로 넘긴다. */
function parseHandle(param: string): string | null {
  const decoded = decodeRouteParam(param)
  return decoded.startsWith('@') ? decoded.slice(1) : null
}

async function fetchProfile(handle: string): Promise<PublicProfile | null> {
  const response = await fetch(`${API_BASE}/api/v1/users/@${encodeURIComponent(handle)}`, {
    next: { revalidate: REVALIDATE_SECONDS, tags: [`profile:${handle}`] },
  })
  return response.ok ? ((await response.json()) as PublicProfile) : null
}

async function fetchRecords(handle: string): Promise<RecordPage> {
  const response = await fetch(
    `${API_BASE}/api/v1/users/@${encodeURIComponent(handle)}/records?limit=20`,
    { next: { revalidate: REVALIDATE_SECONDS, tags: [`profile:${handle}:records`] } },
  )
  return response.ok
    ? ((await response.json()) as RecordPage)
    : { items: [], nextCursor: null, totalCount: 0 }
}

async function fetchCollections(handle: string): Promise<CollectionSummary[]> {
  const response = await fetch(
    `${API_BASE}/api/v1/users/@${encodeURIComponent(handle)}/collections`,
    { next: { revalidate: REVALIDATE_SECONDS, tags: [`profile:${handle}:collections`] } },
  )
  return response.ok ? ((await response.json()) as CollectionSummary[]) : []
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ handle: string }>
}): Promise<Metadata> {
  const handle = parseHandle((await params).handle)
  if (!handle) return {}

  const profile = await fetchProfile(handle)
  if (!profile) return { title: '찾을 수 없는 페이지' }

  const title = `${profile.nickname} (@${profile.handle})`
  const description = profile.bio ?? `${profile.nickname} 님이 읽고, 보고, 들은 것들`

  return {
    title,
    description,
    // 공유 시 미리보기 카드가 곧 유입 채널이다 (ADR-001)
    openGraph: { title, description, type: 'profile', url: `/@${profile.handle}` },
    twitter: { card: 'summary', title, description },
    alternates: { canonical: `/@${profile.handle}` },
  }
}

export default async function ProfilePage({ params }: { params: Promise<{ handle: string }> }) {
  const handle = parseHandle((await params).handle)
  if (!handle) notFound()

  const profile = await fetchProfile(handle)
  if (!profile) notFound()

  // 두 호출은 서로 의존하지 않으므로 병렬로 보낸다
  const [records, collections] = await Promise.all([fetchRecords(handle), fetchCollections(handle)])

  return (
    <main className={stack({ gap: '6', maxW: '2xl', mx: 'auto', px: '6', py: '12' })}>
      {/* 구조화 데이터 — 검색엔진이 프로필로 인식하게 한다 (NFR-06) */}
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{
          __html: JSON.stringify({
            '@context': 'https://schema.org',
            '@type': 'ProfilePage',
            mainEntity: {
              '@type': 'Person',
              name: profile.nickname,
              alternateName: `@${profile.handle}`,
              description: profile.bio ?? undefined,
            },
          }),
        }}
      />

      <header className={stack({ gap: '2' })}>
        <h1 className={css({ textStyle: 'display', color: 'fg.default' })}>{profile.nickname}</h1>
        <p className={css({ textStyle: 'caption', color: 'fg.muted' })}>@{profile.handle}</p>
        {profile.bio && (
          <p className={css({ textStyle: 'body', color: 'fg.default', pt: '1' })}>{profile.bio}</p>
        )}
        <p className={css({ textStyle: 'caption', color: 'fg.muted' })}>
          팔로워 <strong className={css({ color: 'fg.default' })}>{profile.followers}</strong>
          {' · '}
          팔로잉 <strong className={css({ color: 'fg.default' })}>{profile.following}</strong>
        </p>
        {/* 팔로우 상태는 조회자마다 다르다. 캐시된 페이지가 아닌 클라이언트가 판단한다. */}
        <FollowButton handle={profile.handle} />
      </header>

      {collections.length > 0 && (
        <section className={stack({ gap: '3' })}>
          <h2 className={css({ textStyle: 'title' })}>
            컬렉션 <span className={css({ color: 'fg.muted' })}>{collections.length}</span>
          </h2>
          <div className={stack({ gap: '2' })}>
            {collections.map((collection) => (
              <Link
                key={collection.slug}
                href={`/collections/${collection.slug}`}
                className={css({
                  display: 'block',
                  p: '4',
                  rounded: 'md',
                  bg: 'bg.surface',
                  borderWidth: '1px',
                  borderStyle: 'solid',
                  borderColor: 'border.default',
                })}
              >
                <span
                  className={css({ textStyle: 'body', fontWeight: '600', color: 'fg.default' })}
                >
                  {collection.title}
                </span>
                <span className={css({ textStyle: 'caption', color: 'fg.muted', ml: '2' })}>
                  {collection.itemCount}개
                </span>
              </Link>
            ))}
          </div>
        </section>
      )}

      <section className={stack({ gap: '3' })}>
        {/*
          docs/03-architecture.md 의 화면 유형별 페칭 규칙:
          공개 목록은 RSC 로 먼저 그려 SEO·초기 렌더를 확보하고,
          로그인한 팔로워에게만 보이는 FOLLOWERS 기록은 클라이언트가 덧붙인다.
        */}
        <ProfileRecords
          handle={profile.handle}
          initialRecords={records.items}
          initialTotal={records.totalCount}
        />
      </section>
    </main>
  )
}
