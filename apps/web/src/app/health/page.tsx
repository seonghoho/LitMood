import { css } from 'styled-system/css'
import { stack } from 'styled-system/patterns'

export const dynamic = 'force-dynamic'

const API_BASE = process.env.API_INTERNAL_BASE_URL ?? 'http://localhost:8080'

/**
 * 서버 컴포넌트에서 백엔드를 직접 호출하는 예 (docs/03-architecture.md).
 * M0 배선 검증용이며, 실패해도 페이지는 렌더링되어야 한다.
 */
async function fetchApiStatus(): Promise<{ ok: boolean; body: string }> {
  try {
    const response = await fetch(`${API_BASE}/api/v1/ping`, { cache: 'no-store' })
    return { ok: response.ok, body: await response.text() }
  } catch (error) {
    return { ok: false, body: error instanceof Error ? error.message : '알 수 없는 오류' }
  }
}

export default async function HealthPage() {
  const status = await fetchApiStatus()

  return (
    <main className={stack({ gap: '4', maxW: '3xl', mx: 'auto', px: '6', py: '20' })}>
      <h1 className={css({ textStyle: 'title' })}>시스템 상태</h1>
      <p className={css({ textStyle: 'body', color: status.ok ? 'brand.default' : 'fg.muted' })}>
        API: {status.ok ? '정상' : '연결 실패'}
      </p>
      <pre
        className={css({
          textStyle: 'caption',
          fontFamily: 'mono',
          bg: 'bg.subtle',
          color: 'fg.default',
          p: '4',
          rounded: 'md',
          overflowX: 'auto',
        })}
      >
        {status.body}
      </pre>
    </main>
  )
}
