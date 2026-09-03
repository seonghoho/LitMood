import type { ContentRef, Visibility } from '@/features/record/types'

export interface CollectionItem {
  content: ContentRef
  position: number
  note: string | null
}

export interface CollectionSummary {
  slug: string
  title: string
  description: string | null
  coverUrl: string | null
  visibility: Visibility
  itemCount: number
  createdAt: string
}

export interface CollectionResponse extends CollectionSummary {
  ownerHandle: string | null
  ownerNickname: string | null
  items: CollectionItem[]
  updatedAt: string
}
