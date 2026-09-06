import type { ReportTarget } from '@litmood/api-client'

/**
 * 운영 화면의 모델은 백엔드 DTO 에서 생성된다 (ADR-008).
 * 여기서는 생성 타입을 다시 내보내고 화면에서만 쓰는 라벨을 함께 둔다.
 * 타입을 손으로 고치지 마세요 — 백엔드를 고치고 코드젠을 다시 돌립니다.
 */
export type {
  ReportTarget,
  ReportStatus,
  ReportReason,
  AdminReportPage,
  AdminReportResponse,
  AdminReportTarget,
} from '@litmood/api-client'

export const TARGET_LABEL: Record<ReportTarget, string> = {
  RECORD: '기록',
  COLLECTION: '컬렉션',
  USER: '사용자',
}

/**
 * 신고 대상을 확인하러 갈 주소.
 *
 * 지워진 대상은 갈 곳이 없으므로 null 을 준다 — 화면은 링크 대신 "삭제됨" 을 보여준다.
 * 기록은 단건 페이지가 아직 없어(백엔드에는 GET /records/{id} 가 있다) 작성자
 * 프로필로 보낸다. 기록 상세 라우트가 생기면 여기만 바꾸면 된다.
 */
export function targetHref(target: {
  type: ReportTarget
  handle: string | null
  slug: string | null
  deleted: boolean
}): string | null {
  if (target.deleted) return null
  switch (target.type) {
    case 'COLLECTION':
      return target.slug ? `/collections/${encodeURIComponent(target.slug)}` : null
    case 'RECORD':
    case 'USER':
      return target.handle ? `/@${encodeURIComponent(target.handle)}` : null
  }
}
