import type { Metadata } from 'next'
import { stack } from 'styled-system/patterns'
import { ReportQueue } from '@/features/admin/ReportQueue'

/** 운영 화면은 색인 대상이 아니다. 운영자가 아니면 화면 자체가 404 다 (#28). */
export const metadata: Metadata = { title: '신고 처리', robots: { index: false, follow: false } }

export default function AdminReportsPage() {
  return (
    <main className={stack({ gap: '6', maxW: '720px', mx: 'auto', px: '6', py: '12' })}>
      <ReportQueue />
    </main>
  )
}
