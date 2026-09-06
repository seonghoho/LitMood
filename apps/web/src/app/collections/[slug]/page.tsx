import type { Metadata } from 'next'
import Link from 'next/link'
import { notFound } from 'next/navigation'
import { css } from 'styled-system/css'
import { flex, stack } from 'styled-system/patterns'
import { token } from 'styled-system/tokens'
import { fetchCollection } from '@/features/collection/server'
import { CONTENT_TYPE_LABEL } from '@/features/content/types'
import { CollectionLikeButton } from '@/features/social/CollectionLikeButton'

/**
 * 공개 컬렉션 (F-05-04).
 *
 * M3 의 완료 조건은 "링크를 붙였을 때 카드가 뜨는 것"이다.
 * 그래서 이 페이지는 서버 렌더링되고, 옆의 opengraph-image.tsx 가
 * 컬렉션마다 다른 미리보기 이미지를 런타임에 만든다.
 */

const TYPE_TOKEN = {
  BOOK: 'colors.content.book',
  MOVIE: 'colors.content.movie',
  MUSIC: 'colors.content.music',
} as const

export async function generateMetadata({
  params,
}: {
  params: Promise<{ slug: string }>
}): Promise<Metadata> {
  const { slug } = await params
  const collection = await fetchCollection(slug)
  if (!collection) return { title: '찾을 수 없는 컬렉션' }

  const description =
    collection.description ??
    `${collection.ownerNickname ?? '누군가'}가 고른 ${collection.itemCount}개의 콘텐츠`

  // slug 는 한글을 포함할 수 있다. Next 가 자동 생성하는 OG 이미지 URL 은
  // 이미 인코딩된 세그먼트를 한 번 더 인코딩해 %25 가 섞이므로,
  // 여기서 직접 인코딩한 절대 URL 을 지정해 고정한다.
  const path = `/collections/${encodeURIComponent(collection.slug)}`

  return {
    title: collection.title,
    description,
    openGraph: {
      title: collection.title,
      description,
      type: 'article',
      url: path,
      images: [{ url: `${path}/opengraph-image`, width: 1200, height: 630, alt: collection.title }],
    },
    // 카카오톡·트위터 미리보기에서 큰 카드로 뜨게 한다
    twitter: {
      card: 'summary_large_image',
      title: collection.title,
      description,
      images: [`${path}/opengraph-image`],
    },
    alternates: { canonical: path },
  }
}

export default async function CollectionPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params
  const collection = await fetchCollection(slug)
  if (!collection) notFound()

  return (
    <main className={stack({ gap: '8', maxW: '2xl', mx: 'auto', px: '6', py: '12' })}>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{
          __html: JSON.stringify({
            '@context': 'https://schema.org',
            '@type': 'ItemList',
            name: collection.title,
            description: collection.description ?? undefined,
            numberOfItems: collection.itemCount,
            itemListElement: collection.items.map((item, index) => ({
              '@type': 'ListItem',
              position: index + 1,
              name: item.content.title,
            })),
          }),
        }}
      />

      <header className={stack({ gap: '3' })}>
        <h1 className={css({ textStyle: 'display', color: 'fg.default' })}>{collection.title}</h1>
        {collection.description && (
          <p className={css({ textStyle: 'body', color: 'fg.default' })}>
            {collection.description}
          </p>
        )}
        <p className={css({ textStyle: 'caption', color: 'fg.muted' })}>
          {collection.ownerHandle && (
            <Link
              href={`/@${collection.ownerHandle}`}
              className={css({ color: 'fg.muted', textDecoration: 'underline' })}
            >
              {collection.ownerNickname}
            </Link>
          )}
          {' · '}
          {collection.itemCount}개
        </p>
        <div>
          <CollectionLikeButton slug={collection.slug} initialCount={collection.likeCount} />
        </div>
      </header>

      {collection.items.length === 0 ? (
        <p className={css({ textStyle: 'body', color: 'fg.muted' })}>
          아직 담긴 콘텐츠가 없습니다.
        </p>
      ) : (
        <ol className={stack({ gap: '3' })}>
          {collection.items.map((item, index) => (
            <li
              key={item.content.id}
              className={flex({
                gap: '4',
                p: '4',
                rounded: 'md',
                bg: 'bg.surface',
                borderWidth: '1px',
                borderStyle: 'solid',
                borderColor: 'border.default',
                alignItems: 'flex-start',
              })}
            >
              <span
                className={css({
                  textStyle: 'caption',
                  color: 'fg.muted',
                  fontFamily: 'mono',
                  w: '20px',
                  flexShrink: 0,
                  pt: '1',
                })}
              >
                {index + 1}
              </span>

              {item.content.coverUrl ? (
                <img
                  src={item.content.coverUrl}
                  alt=""
                  loading="lazy"
                  className={css({
                    w: '56px',
                    h: '80px',
                    objectFit: 'cover',
                    rounded: 'sm',
                    bg: 'bg.subtle',
                    flexShrink: 0,
                  })}
                />
              ) : (
                <div
                  className={css({
                    w: '56px',
                    h: '80px',
                    rounded: 'sm',
                    bg: 'bg.subtle',
                    flexShrink: 0,
                  })}
                  aria-hidden="true"
                />
              )}

              <div className={stack({ gap: '1', minW: 0, flex: 1 })}>
                <span
                  style={{ backgroundColor: token(TYPE_TOKEN[item.content.type]) }}
                  className={css({
                    textStyle: 'caption',
                    fontSize: '11px',
                    px: '1.5',
                    py: '0.5',
                    rounded: 'sm',
                    color: 'white',
                    alignSelf: 'flex-start',
                  })}
                >
                  {CONTENT_TYPE_LABEL[item.content.type]}
                </span>
                <h2 className={css({ textStyle: 'body', fontWeight: '600', color: 'fg.default' })}>
                  {item.content.title}
                </h2>
                {item.content.creators.length > 0 && (
                  <p className={css({ textStyle: 'caption', color: 'fg.muted' })}>
                    {item.content.creators.join(', ')}
                  </p>
                )}
                {item.note && (
                  <p
                    className={css({
                      textStyle: 'caption',
                      color: 'fg.default',
                      borderLeftWidth: '2px',
                      borderLeftStyle: 'solid',
                      borderColor: 'brand.default',
                      pl: '2',
                      mt: '1',
                    })}
                  >
                    {item.note}
                  </p>
                )}
              </div>
            </li>
          ))}
        </ol>
      )}
    </main>
  )
}
