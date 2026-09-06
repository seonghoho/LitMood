import type { Metadata } from 'next'
import Link from 'next/link'
import { css } from 'styled-system/css'
import { flex, stack } from 'styled-system/patterns'
import { FALLBACK_MOOD_COLOR } from '@litmood/ui'
import { RankedContentList } from '@/features/content/RankedContentList'
import type { ContentRef, MoodTag } from '@/features/record/types'

/**
 * 랜딩 (F-07-02).
 *
 * 비로그인 사용자의 유입 경로다. 그래서 볼 것이 있어야 한다 —
 * "무엇을 하는 서비스인가"를 설명하는 대신 사람들이 실제로 기록한 것을 보여준다.
 *
 * 개인화 요소가 전혀 없는 집계 데이터이므로 캐시된 서버 렌더링이 맞다
 * (docs/03-architecture.md 화면 유형별 페칭 규칙).
 *
 * NOTE: Panda 는 빌드 타임 정적 추출기이므로 `bg: \`mood.${x}\`` 같은
 * 동적 클래스는 추출되지 않는다. 런타임에 결정되는 무드 색은 인라인 스타일로 넘긴다.
 */

export const metadata: Metadata = {
  title: 'LitMood — 읽고 보고 들은 것을 그때의 기분과 함께',
  description:
    '책·영화·음악을 무드와 함께 기록하고 나누는 서비스. 이번 주 사람들이 많이 기록한 것들.',
}

const API_BASE = process.env.API_INTERNAL_BASE_URL ?? 'http://localhost:8080'
const REVALIDATE_SECONDS = 300

type Period = 'week' | 'month'

interface PopularContent {
  content: ContentRef
  recordCount: number
}

async function fetchPopular(period: Period): Promise<PopularContent[]> {
  const response = await fetch(`${API_BASE}/api/v1/discover/popular?period=${period}&limit=10`, {
    next: { revalidate: REVALIDATE_SECONDS, tags: [`discover:popular:${period}`] },
  })
  // 랭킹은 부가 기능이다 — 실패해도 랜딩이 깨지지 않는다
  return response.ok ? ((await response.json()) as PopularContent[]) : []
}

async function fetchMoods(): Promise<MoodTag[]> {
  const response = await fetch(`${API_BASE}/api/v1/moods?limit=12`, {
    next: { revalidate: REVALIDATE_SECONDS, tags: ['moods'] },
  })
  return response.ok ? ((await response.json()) as MoodTag[]) : []
}

export default async function HomePage({
  searchParams,
}: {
  searchParams: Promise<{ period?: string }>
}) {
  // 기간은 서치 파라미터로 받는다 — 클라이언트 상태로 두면 이 페이지가 통째로
  // 클라이언트 컴포넌트가 되어 캐시된 서버 렌더링을 잃는다
  const period: Period = (await searchParams).period === 'month' ? 'month' : 'week'
  const [popular, moods] = await Promise.all([fetchPopular(period), fetchMoods()])

  return (
    <main className={stack({ gap: '10', maxW: '3xl', mx: 'auto', px: '6', py: '16' })}>
      <header className={stack({ gap: '3' })}>
        <h1 className={css({ textStyle: 'display', color: 'fg.default' })}>LitMood</h1>
        <p className={css({ textStyle: 'body', color: 'fg.muted' })}>
          읽고, 보고, 들은 것을{' '}
          <strong className={css({ color: 'brand.default' })}>그때의 기분</strong>과 함께 기록하고
          나누는 서비스.
        </p>
        <div className={flex({ gap: '3', alignItems: 'center', pt: '2' })}>
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
            기록 시작하기
          </Link>
          <Link
            href="/signup"
            className={css({
              textStyle: 'caption',
              color: 'fg.muted',
              textDecoration: 'underline',
            })}
          >
            가입하기
          </Link>
        </div>
      </header>

      <section className={stack({ gap: '4' })}>
        <div className={flex({ gap: '3', alignItems: 'baseline', flexWrap: 'wrap' })}>
          <h2 className={css({ textStyle: 'title' })}>많이 기록된 것들</h2>
          <div className={flex({ gap: '1' })}>
            <PeriodTab period="week" current={period}>
              이번 주
            </PeriodTab>
            <PeriodTab period="month" current={period}>
              이번 달
            </PeriodTab>
          </div>
        </div>

        {popular.length === 0 ? (
          /* 랭킹은 기간 키가 바뀌면 처음부터 다시 쌓인다. 비어 있는 것이 이상한 상태는 아니다. */
          <p className={css({ textStyle: 'body', color: 'fg.muted' })}>
            아직 이 기간에 쌓인 기록이 없습니다. 첫 기록을 남겨 보세요.
          </p>
        ) : (
          <RankedContentList items={popular} countSuffix="명" />
        )}
      </section>

      {moods.length > 0 && (
        <section className={stack({ gap: '4' })}>
          <h2 className={css({ textStyle: 'title' })}>무드로 둘러보기</h2>
          <div className={flex({ gap: '2', flexWrap: 'wrap' })}>
            {moods.map((mood) => (
              <Link
                key={mood.name}
                href={`/moods/${encodeURIComponent(mood.displayName)}`}
                style={{ backgroundColor: mood.color ?? FALLBACK_MOOD_COLOR }}
                className={css({
                  textStyle: 'caption',
                  px: '3',
                  py: '1.5',
                  rounded: 'full',
                  color: 'white',
                })}
              >
                {mood.displayName}
              </Link>
            ))}
          </div>
        </section>
      )}
    </main>
  )
}

function PeriodTab({
  period,
  current,
  children,
}: {
  period: Period
  current: Period
  children: React.ReactNode
}) {
  const selected = period === current
  return (
    <Link
      href={period === 'week' ? '/' : `/?period=${period}`}
      aria-current={selected ? 'page' : undefined}
      className={css({
        textStyle: 'caption',
        px: '3',
        py: '1',
        rounded: 'full',
        borderWidth: '1px',
        borderStyle: 'solid',
        borderColor: selected ? 'brand.default' : 'border.default',
        bg: selected ? 'brand.default' : 'bg.surface',
        color: selected ? 'fg.onAccent' : 'fg.muted',
      })}
    >
      {children}
    </Link>
  )
}
