import { describe, expect, it } from 'vitest'
import {
  buildCollectionPatch,
  buildCoverPatch,
  selectedCoverUrl,
  toDraft,
  type CollectionDraft,
} from '../collection-edit'
import type { CollectionResponse } from '../types'

const collection = (overrides: Partial<CollectionResponse> = {}): CollectionResponse =>
  ({
    slug: 'rainy-abc123',
    title: '비 오는 날',
    description: '원래 설명',
    coverUrl: 'https://cover.example/first.jpg',
    coverPinned: false,
    visibility: 'PUBLIC',
    items: [],
    ...overrides,
  }) as CollectionResponse

const draft = (base: CollectionResponse, overrides: Partial<CollectionDraft> = {}) => ({
  ...toDraft(base),
  ...overrides,
})

describe('buildCollectionPatch', () => {
  it('바뀐 것이 없으면 빈 패치다 — 요청 자체를 보내지 않게 한다', () => {
    const saved = collection()

    expect(buildCollectionPatch(saved, draft(saved))).toEqual({})
  })

  it('제목만 고치면 설명은 실어 보내지 않는다 — 다른 탭의 수정을 덮어쓰지 않는다', () => {
    const saved = collection()

    expect(buildCollectionPatch(saved, draft(saved, { title: '고친 제목' }))).toEqual({
      title: '고친 제목',
    })
  })

  it('설명을 비우면 빈 문자열을 명시한다 — 서버는 null 을 "변경 없음"으로 읽는다', () => {
    const saved = collection()

    expect(buildCollectionPatch(saved, draft(saved, { description: '' }))).toEqual({
      description: '',
    })
  })

  it('제목은 비울 수 없다 — 공유 URL 의 이름이라 빈 값은 보내지 않는다', () => {
    const saved = collection()

    expect(buildCollectionPatch(saved, draft(saved, { title: '   ' }))).toEqual({})
  })

  it('설명이 원래 없었으면 빈 채로 둬도 패치가 생기지 않는다', () => {
    const saved = collection({ description: null })

    expect(buildCollectionPatch(saved, draft(saved))).toEqual({})
  })
})

describe('buildCoverPatch', () => {
  it('"자동"은 빈 문자열로 보낸다 — 지우면 첫 아이템의 표지를 다시 따라간다', () => {
    expect(buildCoverPatch(null)).toEqual({ coverUrl: '' })
  })

  it('고른 표지를 그대로 지정한다', () => {
    expect(buildCoverPatch('https://cover.example/second.jpg')).toEqual({
      coverUrl: 'https://cover.example/second.jpg',
    })
  })
})

describe('selectedCoverUrl', () => {
  it('직접 지정하지 않았으면 아무것도 고르지 않은 상태다', () => {
    // coverUrl 은 첫 아이템에서 끌어온 값이라 "고른 것"이 아니다
    expect(selectedCoverUrl(collection({ coverPinned: false }))).toBeNull()
  })

  it('직접 지정했으면 그 표지가 선택 상태다', () => {
    const pinned = collection({ coverPinned: true, coverUrl: 'https://cover.example/pick.jpg' })

    expect(selectedCoverUrl(pinned)).toBe('https://cover.example/pick.jpg')
  })
})
