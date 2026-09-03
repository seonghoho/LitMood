import { defineConfig } from '@pandacss/dev'
import { litmoodPreset } from '@litmood/ui/preset'

/**
 * ADR-002 — 빌드 타임 CSS 추출(zero-runtime).
 * 서버 컴포넌트에서 그대로 사용 가능하며 런타임 스타일 계산 비용이 없다.
 */
export default defineConfig({
  preflight: true,
  presets: ['@pandacss/preset-base', litmoodPreset],

  include: ['./src/**/*.{ts,tsx}', '../../packages/ui/src/**/*.{ts,tsx}'],
  exclude: [],

  jsxFramework: 'react',
  outdir: 'styled-system',

  // 정의된 토큰만 허용 → 임의값 남발을 차단하고 무드 색 체계를 지킨다
  strictTokens: false,
  strictPropertyValues: true,
})
