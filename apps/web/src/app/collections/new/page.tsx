import type { Metadata } from 'next'
import { css } from 'styled-system/css'
import { stack } from 'styled-system/patterns'
import { CreateCollectionForm } from '@/features/collection/CollectionEditor'

export const metadata: Metadata = { title: '새 컬렉션' }

export default function NewCollectionPage() {
  return (
    <main className={stack({ gap: '6', maxW: '480px', mx: 'auto', px: '6', py: '12' })}>
      <div className={stack({ gap: '2' })}>
        <h1 className={css({ textStyle: 'title' })}>새 컬렉션</h1>
        <p className={css({ textStyle: 'caption', color: 'fg.muted' })}>
          여러 콘텐츠를 하나의 정서로 묶어 공유해 보세요.
        </p>
      </div>
      <CreateCollectionForm />
    </main>
  )
}
