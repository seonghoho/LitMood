/** 타임라인·프로필의 목록 표시 방식 (F-04-03). */
export type ViewMode = 'list' | 'grid'

export const VIEW_MODE_LABEL: Record<ViewMode, string> = {
  list: '리스트',
  grid: '그리드',
}

const STORAGE_KEY = 'litmood:view-mode'

export function parseViewMode(raw: string | null): ViewMode {
  return raw === 'grid' ? 'grid' : 'list'
}

/**
 * 저장된 선택을 읽는다. 서버 저장까지는 과하다 — 취향이고, 기기마다 달라도 이상하지 않다.
 *
 * <p>localStorage 는 접근 자체가 던질 수 있다(사생활 보호 모드, 사이트 데이터 차단).
 * 읽지 못하면 기본값으로 그린다 — 화면이 멈출 이유가 없다.
 */
export function readViewMode(): ViewMode {
  try {
    return parseViewMode(window.localStorage.getItem(STORAGE_KEY))
  } catch {
    return 'list'
  }
}

export function writeViewMode(mode: ViewMode): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, mode)
  } catch {
    // 저장하지 못해도 이번 세션의 전환은 그대로 동작한다
  }
}
