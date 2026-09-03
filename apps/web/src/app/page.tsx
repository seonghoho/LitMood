import Link from 'next/link'
import { css } from 'styled-system/css'
import { flex, stack } from 'styled-system/patterns'
import { token } from 'styled-system/tokens'
import { CURATED_MOOD_TOKENS } from '@litmood/ui'

/**
 * M0 스켈레톤 랜딩.
 * M2 에서 로그인 사용자는 타임라인으로, 비로그인 사용자는 이 페이지로 분기한다.
 *
 * NOTE: Panda 는 빌드 타임 정적 추출기이므로 `bg: \`mood.${x}\`` 같은
 * 동적 클래스는 추출되지 않는다. 런타임에 결정되는 색(무드·콘텐츠 타입)은
 * token() 으로 값을 조회해 인라인 스타일로 넘긴다 — 이것이 zero-runtime 을
 * 유지하면서 동적 색을 다루는 Panda 의 정석이다.
 */
export default function HomePage() {
  return (
    <main className={stack({ gap: '10', maxW: '3xl', mx: 'auto', px: '6', py: '20' })}>
      <header className={stack({ gap: '3' })}>
        <h1 className={css({ textStyle: 'display', color: 'fg.default' })}>LitMood</h1>
        <p className={css({ textStyle: 'body', color: 'fg.muted' })}>
          읽고, 보고, 들은 것을{' '}
          <strong className={css({ color: 'brand.default' })}>그때의 기분</strong>과 함께 기록하고
          나누는 서비스.
        </p>
      </header>

      <section className={stack({ gap: '4' })}>
        <h2 className={css({ textStyle: 'title' })}>무드</h2>
        <div className={flex({ gap: '2', flexWrap: 'wrap' })}>
          {Object.entries(CURATED_MOOD_TOKENS).map(([label, moodToken]) => (
            <span
              key={moodToken}
              style={{ backgroundColor: token(`colors.mood.${moodToken}` as 'colors.mood.dawn') }}
              className={css({
                textStyle: 'caption',
                px: '3',
                py: '1.5',
                rounded: 'full',
                color: 'white',
              })}
            >
              {label}
            </span>
          ))}
        </div>
      </section>

      <section className={stack({ gap: '4' })}>
        <h2 className={css({ textStyle: 'title' })}>콘텐츠</h2>
        <div className={flex({ gap: '3', flexWrap: 'wrap' })}>
          {(
            [
              ['책', 'book'],
              ['영화', 'movie'],
              ['음악', 'music'],
            ] as const
          ).map(([label, type]) => (
            <span
              key={type}
              style={{ borderColor: token(`colors.content.${type}`) }}
              className={css({
                textStyle: 'caption',
                px: '4',
                py: '2',
                rounded: 'md',
                borderWidth: '2px',
                borderStyle: 'solid',
                color: 'fg.default',
              })}
            >
              {label}
            </span>
          ))}
        </div>
      </section>

      <nav className={flex({ gap: '4', alignItems: 'center' })}>
        <Link
          href="/search"
          className={css({
            textStyle: 'body',
            fontWeight: '600',
            px: '4',
            py: '2',
            rounded: 'md',
            bg: 'brand.default',
            color: 'fg.onAccent',
          })}
        >
          검색해 보기
        </Link>
        <Link
          href="/health"
          className={css({ textStyle: 'caption', color: 'fg.muted', textDecoration: 'underline' })}
        >
          시스템 상태
        </Link>
      </nav>
    </main>
  )
}
