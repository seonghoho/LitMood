'use client'

import { useState } from 'react'
import { css } from 'styled-system/css'

/**
 * 스포일러 한줄평 (F-03-08).
 *
 * 가리는 것은 CSS 이고 텍스트는 DOM 에 그대로 있다. 크롤러에는 노출되지만
 * 스포일러는 기밀이 아니라 "실수로 눈에 들어오는 것"을 막는 장치라 이 정도면 충분하다.
 * 서버에서 지우면 공개 페이지의 SEO 본문이 비게 된다.
 */
export function SpoilerReview({ review }: { review: string }) {
  const [revealed, setRevealed] = useState(false)

  if (revealed) {
    return (
      <p className={css({ textStyle: 'caption', color: 'fg.default', lineHeight: '1.6' })}>
        {review}
      </p>
    )
  }

  return (
    <button
      type="button"
      onClick={() => setRevealed(true)}
      aria-label="스포일러 한줄평 보기"
      className={css({
        display: 'block',
        w: 'full',
        textAlign: 'left',
        cursor: 'pointer',
        bg: 'transparent',
        position: 'relative',
      })}
    >
      <span
        aria-hidden="true"
        className={css({
          display: 'block',
          textStyle: 'caption',
          color: 'fg.default',
          lineHeight: '1.6',
          filter: 'blur(4px)',
          userSelect: 'none',
        })}
      >
        {review}
      </span>
      <span className={css({ textStyle: 'caption', color: 'fg.muted' })}>
        스포일러 — 눌러서 보기
      </span>
    </button>
  )
}
