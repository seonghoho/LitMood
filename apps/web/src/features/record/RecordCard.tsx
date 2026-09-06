import Link from 'next/link'
import { css } from 'styled-system/css'
import { flex, stack } from 'styled-system/patterns'
import { token } from 'styled-system/tokens'
import { FALLBACK_MOOD_COLOR } from '@litmood/ui'
import { CONTENT_TYPE_LABEL } from '@/features/content/types'
import { LikeButton } from '@/features/social/LikeButton'
import { SpoilerReview } from './SpoilerReview'
import { STATUS_LABEL, type RecordResponse } from './types'

const TYPE_TOKEN = {
  BOOK: 'colors.content.book',
  MOVIE: 'colors.content.movie',
  MUSIC: 'colors.content.music',
} as const

export function RecordCard({
  record,
  showAuthor,
  own,
  onEdit,
  onDelete,
}: {
  record: RecordResponse
  /** 피드에서는 누가 남긴 기록인지 보여야 한다 */
  showAuthor?: boolean
  /**
   * 내가 쓴 기록인가. 본인에게는 스포일러를 가리지 않는다 (F-03-08).
   * 서버 렌더링 시점에는 조회자를 알 수 없어 false 로 시작한다 —
   * 과하게 가리는 쪽이 안전하고, 하이드레이션 후 교정된다.
   */
  own?: boolean
  /**
   * 본인 기록일 때만 넘긴다 (이슈 #5).
   * 카드가 스스로 판단하지 않는 이유: 공개 프로필은 비로그인 상태로 서버 렌더링되므로
   * 카드에는 "지금 보는 사람이 누구인지"가 없다. 목록을 아는 쪽이 정한다.
   */
  onEdit?: () => void
  onDelete?: () => void
}) {
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

        {record.review &&
          (record.isSpoiler && !own ? (
            <SpoilerReview review={record.review} />
          ) : (
            <p className={css({ textStyle: 'caption', color: 'fg.default', lineHeight: '1.6' })}>
              {record.review}
            </p>
          ))}

        {(record.contextNote || record.startedAt || record.repeatCount > 0) && (
          <p className={css({ textStyle: 'caption', color: 'fg.muted' })}>
            {[
              record.startedAt &&
                (record.finishedAt && record.finishedAt !== record.startedAt
                  ? `${record.startedAt} ~ ${record.finishedAt}`
                  : record.startedAt),
              record.contextNote,
              record.repeatCount > 0 && `${record.repeatCount + 1}번째`,
            ]
              .filter(Boolean)
              .join(' · ')}
          </p>
        )}

        <div className={flex({ gap: '2', alignItems: 'center', mt: '1' })}>
          <LikeButton
            target={{ kind: 'record', id: record.id }}
            initialCount={record.likeCount}
            initialLiked={record.likedByMe}
          />
          {showAuthor && record.authorHandle && (
            <Link
              href={`/@${record.authorHandle}`}
              className={css({ textStyle: 'caption', color: 'fg.muted' })}
            >
              @{record.authorHandle}
            </Link>
          )}

          {(onEdit || onDelete) && (
            <div className={flex({ gap: '2', ml: 'auto' })}>
              {onEdit && (
                <CardAction onClick={onEdit} label={`${content.title} 기록 수정`}>
                  수정
                </CardAction>
              )}
              {onDelete && (
                <CardAction onClick={onDelete} label={`${content.title} 기록 삭제`} danger>
                  삭제
                </CardAction>
              )}
            </div>
          )}
        </div>
      </div>
    </article>
  )
}

function CardAction({
  onClick,
  label,
  danger,
  children,
}: {
  onClick: () => void
  /** 카드가 여러 개라 "수정" 만으로는 무엇을 수정하는지 알 수 없다 */
  label: string
  danger?: boolean
  children: React.ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      className={css({
        textStyle: 'caption',
        px: '2',
        py: '1',
        rounded: 'sm',
        cursor: 'pointer',
        bg: 'transparent',
        color: danger ? 'danger.500' : 'fg.muted',
        _hover: { bg: 'bg.subtle' },
      })}
    >
      {children}
    </button>
  )
}
