import { css } from 'styled-system/css'
import { flex, stack } from 'styled-system/patterns'
import type { ContentRef } from '@/features/record/types'
import { CONTENT_TYPE_LABEL, type ContentType } from './types'

/**
 * 순위 목록. 무드 탐색(F-07-01)과 인기 콘텐츠(F-07-02)가 같은 모양으로 쓴다.
 *
 * 두 화면 모두 "무엇이 얼마나 기록됐나"를 보여주므로 순위·제목·근거 수의
 * 배치가 같아야 한다. 평균 별점은 DB 집계인 무드 쪽에만 있다.
 */
export function RankedContentList({
  items,
  countSuffix = '명',
}: {
  items: { content: ContentRef; recordCount: number; averageRating?: number | null }[]
  /** "3명" / "3개" — 세는 단위가 화면마다 다르다 */
  countSuffix?: string
}) {
  return (
    <ol className={stack({ gap: '3' })}>
      {items.map((ranked, index) => (
        <li
          key={ranked.content.id}
          className={flex({
            gap: '4',
            p: '4',
            rounded: 'md',
            bg: 'bg.surface',
            borderWidth: '1px',
            borderStyle: 'solid',
            borderColor: 'border.default',
            alignItems: 'center',
          })}
        >
          <span
            className={css({
              textStyle: 'title',
              color: 'fg.muted',
              fontFamily: 'mono',
              w: '32px',
              flexShrink: 0,
            })}
          >
            {index + 1}
          </span>

          <div className={stack({ gap: '1', flex: 1, minW: 0 })}>
            <h3 className={css({ textStyle: 'body', fontWeight: '600', color: 'fg.default' })}>
              {ranked.content.title}
            </h3>
            <p className={css({ textStyle: 'caption', color: 'fg.muted' })}>
              {CONTENT_TYPE_LABEL[ranked.content.type as ContentType]}
              {ranked.content.creators.length > 0 && ` · ${ranked.content.creators.join(', ')}`}
            </p>
          </div>

          <div className={stack({ gap: '0.5', alignItems: 'flex-end', flexShrink: 0 })}>
            <span className={css({ textStyle: 'caption', color: 'fg.muted' })}>
              {ranked.recordCount}
              {countSuffix}
            </span>
            {ranked.averageRating != null && (
              <span
                className={css({ textStyle: 'caption', color: 'brand.default', fontWeight: '600' })}
              >
                ★ {ranked.averageRating.toFixed(1)}
              </span>
            )}
          </div>
        </li>
      ))}
    </ol>
  )
}
