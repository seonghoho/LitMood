import Link from 'next/link'
import { css } from 'styled-system/css'
import { flex, stack } from 'styled-system/patterns'
import { token } from 'styled-system/tokens'
import { FALLBACK_MOOD_COLOR } from '@litmood/ui'
import { CONTENT_TYPE_LABEL } from '@/features/content/types'
import { STATUS_LABEL, type RecordResponse } from './types'

const TYPE_TOKEN = {
  BOOK: 'colors.content.book',
  MOVIE: 'colors.content.movie',
  MUSIC: 'colors.content.music',
} as const

export function RecordCard({ record }: { record: RecordResponse }) {
  const { content } = record

  return (
    <article
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
      {content.coverUrl ? (
        <img
          src={content.coverUrl}
          alt=""
          loading="lazy"
          className={css({
            w: '64px',
            h: '92px',
            objectFit: 'cover',
            rounded: 'sm',
            bg: 'bg.subtle',
            flexShrink: 0,
          })}
        />
      ) : (
        <div
          className={css({ w: '64px', h: '92px', rounded: 'sm', bg: 'bg.subtle', flexShrink: 0 })}
          aria-hidden="true"
        />
      )}

      <div className={stack({ gap: '2', minW: 0, flex: 1 })}>
        <div className={flex({ gap: '2', alignItems: 'center', flexWrap: 'wrap' })}>
          <span
            style={{ backgroundColor: token(TYPE_TOKEN[content.type]) }}
            className={css({
              textStyle: 'caption',
              fontSize: '11px',
              px: '1.5',
              py: '0.5',
              rounded: 'sm',
              color: 'white',
            })}
          >
            {CONTENT_TYPE_LABEL[content.type]}
          </span>
          <span className={css({ textStyle: 'caption', color: 'fg.muted' })}>
            {STATUS_LABEL[record.status]}
          </span>
          {/* != null 은 null 과 undefined 를 함께 거른다 — 서버가 필드를 생략해도 안전하다 */}
          {record.rating != null && (
            <span
              className={css({ textStyle: 'caption', color: 'brand.default', fontWeight: '600' })}
            >
              ★ {record.rating.toFixed(1)}
            </span>
          )}
        </div>

        <h3 className={css({ textStyle: 'body', fontWeight: '600', color: 'fg.default' })}>
          {content.title}
        </h3>

        {content.creators.length > 0 && (
          <p className={css({ textStyle: 'caption', color: 'fg.muted' })}>
            {content.creators.join(', ')}
          </p>
        )}

        {record.moods.length > 0 && (
          <div className={flex({ gap: '1.5', flexWrap: 'wrap' })}>
            {/* 무드는 탐색의 축이다 (F-07-01) — 칩에서 바로 같은 기분의 목록으로 간다 */}
            {record.moods.map((mood) => (
              <Link
                key={mood.name}
                href={`/moods/${encodeURIComponent(mood.displayName)}`}
                style={{ backgroundColor: mood.color ?? FALLBACK_MOOD_COLOR }}
                className={css({
                  textStyle: 'caption',
                  fontSize: '11px',
                  px: '2',
                  py: '0.5',
                  rounded: 'full',
                  color: 'white',
                })}
              >
                {mood.displayName}
              </Link>
            ))}
          </div>
        )}

        {record.review && (
          <p className={css({ textStyle: 'caption', color: 'fg.default', lineHeight: '1.6' })}>
            {record.review}
          </p>
        )}
      </div>
    </article>
  )
}
