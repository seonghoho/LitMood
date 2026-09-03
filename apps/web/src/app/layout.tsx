import type { Metadata } from 'next'
import type { ReactNode } from 'react'
import { AuthProvider } from '@/features/auth/AuthProvider'
import { NavBar } from '@/shared/components/NavBar'
import './globals.css'

const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL ?? 'http://localhost:3000'

export const metadata: Metadata = {
  metadataBase: new URL(SITE_URL),
  title: {
    default: 'LitMood — 읽고, 보고, 들은 것을 기분과 함께',
    template: '%s · LitMood',
  },
  description: '책·영화·음악을 하나의 타임라인에 기록하고, 무드로 묶어 나누는 서비스',
  openGraph: {
    type: 'website',
    locale: 'ko_KR',
    siteName: 'LitMood',
  },
}

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="ko" suppressHydrationWarning>
      <body>
        <AuthProvider>
          <NavBar />
          {children}
        </AuthProvider>
      </body>
    </html>
  )
}
