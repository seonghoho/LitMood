/**
 * DB 의 무드 이름(한국어)과 디자인 토큰 키를 잇는 매핑.
 * 서버는 `moods.color` 를 함께 내려주지만, 토큰 키를 알면
 * OG 이미지나 정적 렌더링에서 서버 응답 없이도 색을 결정할 수 있다.
 */
export const CURATED_MOOD_TOKENS = {
  새벽: 'dawn',
  비오는날: 'rainy',
  설렘: 'flutter',
  먹먹함: 'heavy',
  몰입: 'immersion',
  위로: 'comfort',
  번아웃: 'burnout',
  여행: 'travel',
  가을밤: 'autumnNight',
  집중: 'focus',
  향수: 'nostalgia',
  해방: 'freedom',
} as const

export type CuratedMoodName = keyof typeof CURATED_MOOD_TOKENS

/** 자유 입력 무드의 기본 색 — 큐레이션 무드가 아니면 중립색으로 표시한다. */
export const FALLBACK_MOOD_COLOR = '#78716C'

export function isCuratedMood(name: string): name is CuratedMoodName {
  return name in CURATED_MOOD_TOKENS
}

/**
 * 무드 이름 정규화 (도메인 불변식 5).
 * 백엔드와 동일한 규칙을 적용해야 클라이언트 표시와 서버 저장이 어긋나지 않는다.
 */
export function normalizeMoodName(raw: string): string {
  return raw.trim().replace(/^#/, '').replace(/\s+/g, '').toLowerCase()
}
