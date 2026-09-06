import type { ReportReason } from '@litmood/api-client'

/**
 * 신고 대상 (F-06-05). 대상은 URL 이 가리키므로 화면이 아는 주소를 그대로 담는다 —
 * 기록은 id, 컬렉션은 slug, 사용자는 handle.
 */
export type ReportTarget =
  | { kind: 'record'; id: number; label: string }
  | { kind: 'collection'; slug: string; label: string }
  | { kind: 'user'; handle: string; label: string }

export function reportPath(target: ReportTarget): string {
  switch (target.kind) {
    case 'record':
      return `/api/v1/records/${target.id}/report`
    case 'collection':
      return `/api/v1/collections/${encodeURIComponent(target.slug)}/report`
    case 'user':
      return `/api/v1/users/@${encodeURIComponent(target.handle)}/report`
  }
}

/** 사유의 정의는 백엔드 enum 이고, 한국어 라벨만 화면이 갖는다 (VISIBILITY_LABEL 과 같은 방식). */
export const REPORT_REASONS: ReportReason[] = [
  'SPAM',
  'ABUSE',
  'SEXUAL',
  'SPOILER',
  'COPYRIGHT',
  'OTHER',
]

export const REPORT_REASON_LABEL: Record<ReportReason, string> = {
  SPAM: '스팸·광고',
  ABUSE: '욕설·괴롭힘',
  SEXUAL: '선정적인 내용',
  SPOILER: '스포일러를 가리지 않음',
  COPYRIGHT: '저작권 침해',
  OTHER: '기타',
}
