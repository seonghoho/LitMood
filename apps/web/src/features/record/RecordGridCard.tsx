'use client'

import Link from 'next/link'
import { useState } from 'react'
import { css } from 'styled-system/css'
import { flex, stack } from 'styled-system/patterns'
import { token } from 'styled-system/tokens'
import { FALLBACK_MOOD_COLOR } from '@litmood/ui'
import { CONTENT_TYPE_LABEL } from '@/features/content/types'
import { LikeButton } from '@/features/social/LikeButton'
import { ReportButton } from '@/features/social/ReportButton'
import { CardAction } from './CardAction'
import { type RecordResponse } from './types'

const TYPE_TOKEN = {
  BOOK: 'colors.content.book',
  MOVIE: 'colors.content.movie',
  MUSIC: 'colors.content.music',
} as const

/**
 * 커버 위주 카드 (F-04-03 그리드 뷰).
 *
 * 기록이 쌓이면 리스트로 훑기 어려워진다. 책·영화·음악은 표지가 곧 식별자라
 * 커버 그리드가 "내가 뭘 봤더라"를 훨씬 빨리 답해 준다.
 *
 * 한줄평과 맥락은 싣지 않는다 — 그리드는 훑는 화면이고, 읽을 것이 필요하면
 * 리스트로 돌아가면 된다. 덕분에 스포일러가 그리드에 새어 나올 일도 없다 (F-03-08).
 */
export function RecordGridCard({
  record,
  own,
  onEdit,
  onDelete,
}: {
  record: RecordResponse
  own?: boolean
  onEdit?: () => void
  onDelete?: () => void
}) {
  const { content } = record
  const accent = token(TYPE_TOKEN[content.type])

  return (
    <article className={stack({ gap: '2' })}>
      <div
        style={{ borderColor: accent }}
        className={css({
          position: 'relative',
          aspectRatio: '3 / 4',
          rounded: 'md',
          overflow: 'hidden',
          bg: 'bg.subtle',
          borderWidth: '1px',
          borderStyle: 'solid',
        })}
      >
        <Cover
          coverUrl={content.coverUrl}
          typeLabel={CONTENT_TYPE_LABEL[content.type]}
          accent={accent}
        />

        {record.rating != null && (
          <span
            className={css({
              position: 'absolute',
              top: '1',
              right: '1',
              textStyle: 'caption',
              fontSize: '11px',
              px: '1.5',
              py: '0.5',
              rounded: 'sm',
              bg: 'rgba(0,0,0,0.6)',
              color: 'white',
            })}
          >
            ★ {record.rating.toFixed(1)}
          </span>
        )}
      </div>

      <h3
        className={css({
          textStyle: 'caption',
          fontWeight: '600',
          color: 'fg.default',
          lineHeight: '1.4',
        })}
      >
        {content.title}
      </h3>

      {record.moods.length > 0 && (
        <div className={flex({ gap: '1', flexWrap: 'wrap' })}>
          {record.moods.slice(0, 2).map((mood) => (
            <Link
              key={mood.name}
              href={`/moods/${encodeURIComponent(mood.displayName)}`}
              style={{ backgroundColor: mood.color ?? FALLBACK_MOOD_COLOR }}
              className={css({
                textStyle: 'caption',
                fontSize: '10px',
                px: '1.5',
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

      <div className={flex({ gap: '1', alignItems: 'center' })}>
        <LikeButton
          target={{ kind: 'record', id: record.id }}
          initialCount={record.likeCount}
          initialLiked={record.likedByMe}
        />
        <div className={flex({ gap: '1', ml: 'auto' })}>
          {!own && record.authorHandle && (
            <ReportButton
              target={{ kind: 'record', id: record.id, label: content.title }}
              ownerHandle={record.authorHandle}
            />
          )}
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
      </div>
    </article>
  )
}

/**
 * 표지. 커버 위주 화면이라 <b>깨진 이미지가 그대로 보이면 안 된다</b> —
 * URL 은 provider 가 준 것이고 죽은 링크가 섞인다(스텁 데이터에도 있다).
 * 로드 실패는 "표지 없음"과 같게 다뤄 타입 색으로 대체한다.
 */
function Cover({
  coverUrl,
  typeLabel,
  accent,
}: {
  coverUrl: string | null
  typeLabel: string
  accent: string
}) {
  const [failed, setFailed] = useState(false)

  if (!coverUrl || failed) {
    return (
      <div
        style={{ backgroundColor: accent }}
        className={css({
          position: 'absolute',
          inset: '0',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'white',
          textStyle: 'caption',
          opacity: 0.85,
        })}
      >
        {typeLabel}
      </div>
    )
  }

  return (
    <img
      src={coverUrl}
      alt=""
      loading="lazy"
      onError={() => setFailed(true)}
      // 리셋의 img { height: auto } 가 유틸리티 클래스를 이긴다. 대체 요소는
      // top/bottom 을 0 으로 줘도 늘어나지 않고 원본 비율에서 멈추므로
      // 크기만 인라인으로 못박는다.
      style={{ width: '100%', height: '100%' }}
      className={css({ position: 'absolute', inset: '0', objectFit: 'cover' })}
    />
  )
}
