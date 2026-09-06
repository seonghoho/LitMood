import type { Metadata } from 'next'
import { css } from 'styled-system/css'
import { stack } from 'styled-system/patterns'
import { ProfileSettingsForm } from '@/features/profile/ProfileSettingsForm'
import { BlockedUsers } from '@/features/social/BlockedUsers'

export const metadata: Metadata = { title: '프로필 설정' }

export default function SettingsPage() {
  return (
    <main className={stack({ gap: '6', maxW: '520px', mx: 'auto', px: '6', py: '12' })}>
      <h1 className={css({ textStyle: 'title' })}>프로필 설정</h1>
      <ProfileSettingsForm />
      <BlockedUsers />
    </main>
  )
}
