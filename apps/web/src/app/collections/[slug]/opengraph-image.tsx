import { ImageResponse } from 'next/og'
import { fetchCollection } from '@/features/collection/server'

/**
 * 컬렉션별 동적 OG 이미지 (F-05-04).
 *
 * ADR-001 에서 Next 를 고른 이유 중 하나. 컬렉션마다 다른 미리보기 카드가
 * 필요한데, next/og 는 이를 런타임에 만들어 준다 — 별도 이미지 서버가 없어도 된다.
 *
 * 주의: 이 파일은 Edge 런타임에서 돌고 JSX 를 SVG 로 렌더한다.
 * Panda 클래스는 여기서 동작하지 않으므로 인라인 스타일만 쓴다.
 * flex 레이아웃만 지원되며 자식이 둘 이상인 요소는 display:flex 가 필수다.
 */

export const size = { width: 1200, height: 630 }
export const contentType = 'image/png'
export const alt = 'LitMood 컬렉션'

// 무드 팔레트와 같은 계열 — 브랜드 일관성 (ADR-002)
const BG = '#1C1917'
const ACCENT = '#C9A227'
const MUTED = '#A8A29E'

const TYPE_COLOR: Record<string, string> = {
  BOOK: '#B4654A',
  MOVIE: '#3F5B8B',
  MUSIC: '#4E7A5E',
}

export default async function Image({ params }: { params: { slug: string } }) {
  const collection = await fetchCollection(params.slug)

  if (!collection) {
    return new ImageResponse(
      <div
        style={{
          width: '100%',
          height: '100%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: BG,
          color: ACCENT,
          fontSize: 64,
          fontWeight: 700,
        }}
      >
        LitMood
      </div>,
      size,
    )
  }

  // 표지가 있는 앞쪽 5개만 — 이미지가 많을수록 생성이 느려진다
  const covers = collection.items.filter((item) => item.content.coverUrl).slice(0, 5)

  return new ImageResponse(
    <div
      style={{
        width: '100%',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'space-between',
        background: BG,
        padding: 64,
      }}
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
        <div style={{ display: 'flex', color: ACCENT, fontSize: 26, letterSpacing: 2 }}>
          LITMOOD
        </div>

        <div
          style={{
            display: 'flex',
            color: '#FAFAF9',
            fontSize: collection.title.length > 24 ? 60 : 76,
            fontWeight: 700,
            lineHeight: 1.15,
          }}
        >
          {collection.title}
        </div>

        {collection.description ? (
          <div style={{ display: 'flex', color: MUTED, fontSize: 30, lineHeight: 1.4 }}>
            {collection.description.slice(0, 70)}
          </div>
        ) : null}
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 28 }}>
        {covers.length > 0 ? (
          <div style={{ display: 'flex', gap: 16 }}>
            {covers.map((item) => (
              <img
                key={item.content.id}
                src={item.content.coverUrl as string}
                width={126}
                height={180}
                style={{
                  objectFit: 'cover',
                  borderRadius: 8,
                  border: `2px solid ${TYPE_COLOR[item.content.type] ?? MUTED}`,
                }}
              />
            ))}
          </div>
        ) : null}

        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 16,
            color: MUTED,
            fontSize: 28,
          }}
        >
          <div style={{ display: 'flex', color: '#FAFAF9' }}>
            {collection.ownerNickname ?? 'LitMood'}
          </div>
          <div style={{ display: 'flex' }}>·</div>
          <div style={{ display: 'flex' }}>{collection.itemCount}개의 콘텐츠</div>
        </div>
      </div>
    </div>,
    size,
  )
}
