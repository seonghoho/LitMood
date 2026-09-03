import { css } from 'styled-system/css'
import { flex, stack } from 'styled-system/patterns'
import { token } from 'styled-system/tokens'
import { CONTENT_TYPE_LABEL, type ContentSummary } from './types'

const TYPE_TOKEN = {
  BOOK: 'colors.content.book',
  MOVIE: 'colors.content.movie',
  MUSIC: 'colors.content.music',
} as const

export function ContentCard({
  content,
  action,
}: {
  content: ContentSummary
  /** 우측 액션 슬롯 — 검색 결과에서는 "기록" 버튼이 들어간다 */
  action?: React.ReactNode
}) {
  const accent = token(TYPE_TOKEN[content.type])

  return (
    <article
      className={flex({
        gap: '4',
        p: '3',
        rounded: 'md',
        bg: 'bg.surface',
        borderWidth: '1px',
        borderStyle: 'solid',
        borderColor: 'border.default',
        alignItems: 'flex-start',
      })}
    >
      {/* next/image 대신 img 를 쓰는 이유: 커버 URL 호스트가 provider 마다 다르고
          외부 호스트가 늘어날 때마다 next.config 를 고쳐야 하는 결합을 피한다.
          최적화가 필요해지면 자체 이미지 프록시를 두는 편이 낫다. */}
      {content.coverUrl ? (
        <img
          src={content.coverUrl}
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
          className={css({ w: '56px', h: '80px', rounded: 'sm', bg: 'bg.subtle', flexShrink: 0 })}
          aria-hidden="true"
        />
      )}

      <div className={stack({ gap: '1', minW: 0, flex: 1 })}>
        <div className={flex({ gap: '2', alignItems: 'center' })}>
          <span
            style={{ backgroundColor: accent }}
            className={css({
              textStyle: 'caption',
              fontSize: '11px',
              px: '1.5',
              py: '0.5',
              rounded: 'sm',
              color: 'white',
              flexShrink: 0,
            })}
          >
            {CONTENT_TYPE_LABEL[content.type]}
          </span>
          {content.releasedOn && (
            <span className={css({ textStyle: 'caption', color: 'fg.muted' })}>
              {content.releasedOn.slice(0, 4)}
            </span>
          )}
        </div>

        <h3
          className={css({
            textStyle: 'body',
            fontWeight: '600',
            color: 'fg.default',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          })}
        >
          {content.title}
        </h3>

        {content.creators.length > 0 && (
          <p
            className={css({
              textStyle: 'caption',
              color: 'fg.muted',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            })}
          >
            {content.creators.join(', ')}
          </p>
        )}
      </div>

      {action && <div className={css({ flexShrink: 0, alignSelf: 'center' })}>{action}</div>}
    </article>
  )
}
