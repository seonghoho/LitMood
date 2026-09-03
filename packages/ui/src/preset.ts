import { definePreset, defineSemanticTokens, defineTokens } from '@pandacss/dev'

/**
 * LitMood 디자인 토큰.
 *
 * 이 서비스에서 무드는 1급 개념이므로(docs/00-product-overview.md),
 * 무드 색을 임의값이 아닌 **토큰**으로 정의한다. 그래야 타임라인 카드·필터 칩·
 * 컬렉션 커버·OG 이미지에서 같은 무드가 항상 같은 색으로 보인다.
 * 값은 V2 마이그레이션의 moods.color 와 일치해야 한다.
 */
const tokens = defineTokens({
  colors: {
    // 무드 팔레트 — DB 시드(V2__seed_curated_moods.sql)와 1:1 대응
    mood: {
      dawn: { value: '#2A3B6B' }, // 새벽
      rainy: { value: '#4A6670' }, // 비 오는 날
      flutter: { value: '#E8859B' }, // 설렘
      heavy: { value: '#6B5B7B' }, // 먹먹함
      immersion: { value: '#1F4E4A' }, // 몰입
      comfort: { value: '#C9A227' }, // 위로
      burnout: { value: '#7A4E3B' }, // 번아웃
      travel: { value: '#2E7D9A' }, // 여행
      autumnNight: { value: '#8C5A3C' }, // 가을밤
      focus: { value: '#37474F' }, // 집중
      nostalgia: { value: '#A47551' }, // 향수
      freedom: { value: '#3E8E5A' }, // 해방
    },
    // 콘텐츠 타입 구분색 — 통합 타임라인에서 종류를 즉시 식별하기 위한 최소 장치
    content: {
      book: { value: '#B4654A' },
      movie: { value: '#3F5B8B' },
      music: { value: '#4E7A5E' },
    },
    neutral: {
      50: { value: '#FAFAF9' },
      100: { value: '#F5F5F4' },
      200: { value: '#E7E5E4' },
      300: { value: '#D6D3D1' },
      400: { value: '#A8A29E' },
      500: { value: '#78716C' },
      600: { value: '#57534E' },
      700: { value: '#44403C' },
      800: { value: '#292524' },
      900: { value: '#1C1917' },
      950: { value: '#0C0A09' },
    },
    accent: {
      500: { value: '#C9A227' }, // 브랜드 — "불 켜진(lit)" 따뜻한 황금빛
      600: { value: '#A8871F' },
    },
    danger: { 500: { value: '#DC2626' } },
  },

  fonts: {
    // Pretendard: 한글 본문 가독성이 좋고 라틴 자소와 높이가 맞는다
    body: { value: 'Pretendard Variable, Pretendard, -apple-system, system-ui, sans-serif' },
    mono: { value: 'ui-monospace, SFMono-Regular, Menlo, monospace' },
  },

  radii: {
    sm: { value: '4px' },
    md: { value: '8px' },
    lg: { value: '12px' },
    full: { value: '9999px' },
  },
})

/**
 * 시맨틱 토큰 — 컴포넌트는 원시 색이 아니라 **역할**을 참조한다.
 * 다크 모드 대응이 여기 한 곳에서 끝난다.
 */
const semanticTokens = defineSemanticTokens({
  colors: {
    bg: {
      canvas: { value: { base: '{colors.neutral.50}', _dark: '{colors.neutral.950}' } },
      surface: { value: { base: '#FFFFFF', _dark: '{colors.neutral.900}' } },
      subtle: { value: { base: '{colors.neutral.100}', _dark: '{colors.neutral.800}' } },
    },
    fg: {
      default: { value: { base: '{colors.neutral.900}', _dark: '{colors.neutral.100}' } },
      muted: { value: { base: '{colors.neutral.500}', _dark: '{colors.neutral.400}' } },
      onAccent: { value: { base: '{colors.neutral.950}', _dark: '{colors.neutral.950}' } },
    },
    border: {
      default: { value: { base: '{colors.neutral.200}', _dark: '{colors.neutral.700}' } },
    },
    brand: {
      default: { value: { base: '{colors.accent.500}', _dark: '{colors.accent.500}' } },
      hover: { value: { base: '{colors.accent.600}', _dark: '{colors.accent.600}' } },
    },
  },
})

export const litmoodPreset = definePreset({
  name: 'litmood',
  theme: {
    extend: {
      tokens,
      semanticTokens,
      textStyles: {
        display: { value: { fontSize: '2rem', fontWeight: '700', lineHeight: '1.25' } },
        title: { value: { fontSize: '1.25rem', fontWeight: '600', lineHeight: '1.4' } },
        body: { value: { fontSize: '0.9375rem', fontWeight: '400', lineHeight: '1.6' } },
        caption: { value: { fontSize: '0.8125rem', fontWeight: '400', lineHeight: '1.5' } },
      },
    },
  },
  conditions: {
    extend: {
      dark: '[data-theme="dark"] &, .dark &',
    },
  },
})

export default litmoodPreset
