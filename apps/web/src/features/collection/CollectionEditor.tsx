'use client'

import { useRouter } from 'next/navigation'
import { useState } from 'react'
import { css } from 'styled-system/css'
import { flex, stack } from 'styled-system/patterns'
import { ApiError } from '@litmood/api-client'
import type { ContentSummary } from '@/features/content/types'
import { VISIBILITY_LABEL, type Visibility } from '@/features/record/types'
import { apiDelete, apiPatch, apiPost } from '@/shared/lib/api'
import type { CollectionResponse } from './types'

const VISIBILITIES: Visibility[] = ['PUBLIC', 'FOLLOWERS', 'PRIVATE']

/** 컬렉션 생성 (F-05-01). 만들자마자 공개 페이지로 보내 공유 링크를 바로 준다. */
export function CreateCollectionForm() {
  const router = useRouter()
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [visibility, setVisibility] = useState<Visibility>('PUBLIC')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      const created = await apiPost<CollectionResponse>('/api/v1/collections', {
        title,
        description: description.trim() || null,
        visibility,
      })
      router.push(`/collections/${created.slug}/edit`)
    } catch (e) {
      setError(e instanceof ApiError ? e.problem.title : '컬렉션을 만들지 못했습니다')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={submit} className={stack({ gap: '4' })}>
      <label className={stack({ gap: '1.5' })}>
        <span className={css({ textStyle: 'caption', color: 'fg.muted' })}>제목</span>
        <input
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          placeholder="비 오는 날 듣는 앨범"
          maxLength={100}
          required
          className={inputStyle}
        />
      </label>

      <label className={stack({ gap: '1.5' })}>
        <span className={css({ textStyle: 'caption', color: 'fg.muted' })}>설명</span>
        <textarea
          value={description}
          onChange={(event) => setDescription(event.target.value)}
          rows={3}
          maxLength={500}
          placeholder="어떤 정서로 묶었나요?"
          className={css({ ...inputBase, resize: 'vertical' })}
        />
      </label>

      <div className={stack({ gap: '2' })}>
        <span className={css({ textStyle: 'caption', color: 'fg.muted' })}>공개 범위</span>
        <div className={flex({ gap: '2' })}>
          {VISIBILITIES.map((v) => (
            <button
              key={v}
              type="button"
              onClick={() => setVisibility(v)}
              aria-pressed={v === visibility}
              className={chipStyle(v === visibility)}
            >
              {VISIBILITY_LABEL[v]}
            </button>
          ))}
        </div>
      </div>

      {error && (
        <p role="alert" className={css({ textStyle: 'caption', color: 'danger.500' })}>
          {error}
        </p>
      )}

      <button type="submit" disabled={submitting} className={primaryButton}>
        {submitting ? '만드는 중…' : '만들기'}
      </button>
    </form>
  )
}

/** 컬렉션 편집 — 콘텐츠 추가·제거·정렬 (F-05-02). */
export function CollectionEditor({ initial }: { initial: CollectionResponse }) {
  const [collection, setCollection] = useState(initial)
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<ContentSummary[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const search = async (event: React.FormEvent) => {
    event.preventDefault()
    if (query.trim().length < 2) return
    setBusy(true)
    try {
      const response = await fetch(
        `${process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080'}/api/v1/contents/search?q=${encodeURIComponent(query)}&limit=5`,
      )
      const data = (await response.json()) as {
        results: Record<string, ContentSummary[]>
      }
      setResults(Object.values(data.results).flat())
    } finally {
      setBusy(false)
    }
  }

  const run = async (action: () => Promise<CollectionResponse>) => {
    setBusy(true)
    setError(null)
    try {
      setCollection(await action())
    } catch (e) {
      setError(e instanceof ApiError ? e.problem.title : '변경하지 못했습니다')
    } finally {
      setBusy(false)
    }
  }

  const addItem = (content: ContentSummary) =>
    run(() =>
      apiPost<CollectionResponse>(`/api/v1/collections/${collection.slug}/items`, {
        provider: content.provider,
        externalId: content.externalId,
      }),
    )

  const removeItem = (contentId: number) =>
    run(() =>
      apiDelete(`/api/v1/collections/${collection.slug}/items/${contentId}`).then(() =>
        collectionWithout(collection, contentId),
      ),
    )

  /** 순서는 큐레이션의 의미다 — 위/아래 이동으로 표현한다. */
  const move = (index: number, delta: number) => {
    const next = [...collection.items]
    const target = index + delta
    if (target < 0 || target >= next.length) return
    ;[next[index], next[target]] = [next[target]!, next[index]!]

    return run(() =>
      apiPatch<CollectionResponse>(`/api/v1/collections/${collection.slug}/items/order`, {
        contentIds: next.map((item) => item.content.id),
      }),
    )
  }

  return (
    <div className={stack({ gap: '6' })}>
      <div className={stack({ gap: '3' })}>
        <h2 className={css({ textStyle: 'title' })}>담긴 콘텐츠 {collection.itemCount}</h2>
        {collection.items.length === 0 && (
          <p className={css({ textStyle: 'caption', color: 'fg.muted' })}>
            아래에서 검색해 콘텐츠를 담아 보세요.
          </p>
        )}
        <ol className={stack({ gap: '2' })}>
          {collection.items.map((item, index) => (
            <li key={item.content.id} className={itemRow}>
              <span className={css({ textStyle: 'caption', color: 'fg.muted', w: '20px' })}>
                {index + 1}
              </span>
              <span className={css({ textStyle: 'body', flex: 1, color: 'fg.default' })}>
                {item.content.title}
              </span>
              <button
                type="button"
                onClick={() => void move(index, -1)}
                disabled={busy || index === 0}
                aria-label="위로"
                className={iconButton}
              >
                ↑
              </button>
              <button
                type="button"
                onClick={() => void move(index, 1)}
                disabled={busy || index === collection.items.length - 1}
                aria-label="아래로"
                className={iconButton}
              >
                ↓
              </button>
              <button
                type="button"
                onClick={() => void removeItem(item.content.id)}
                disabled={busy}
                aria-label="제거"
                className={iconButton}
              >
                ✕
              </button>
            </li>
          ))}
        </ol>
      </div>

      <form onSubmit={search} className={stack({ gap: '3' })}>
        <h2 className={css({ textStyle: 'title' })}>콘텐츠 추가</h2>
        <div className={flex({ gap: '2' })}>
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="제목, 작가, 아티스트로 검색"
            className={css({ ...inputBase, flex: 1 })}
          />
          <button type="submit" disabled={busy} className={primaryButton}>
            검색
          </button>
        </div>

        <div className={stack({ gap: '2' })}>
          {results.map((content) => {
            const already = collection.items.some(
              (item) => item.content.externalId === content.externalId,
            )
            return (
              <div key={`${content.provider}:${content.externalId}`} className={itemRow}>
                <span className={css({ textStyle: 'body', flex: 1, color: 'fg.default' })}>
                  {content.title}
                </span>
                <button
                  type="button"
                  onClick={() => void addItem(content)}
                  disabled={busy || already}
                  className={css({
                    textStyle: 'caption',
                    px: '3',
                    py: '1.5',
                    rounded: 'md',
                    cursor: 'pointer',
                    bg: already ? 'bg.subtle' : 'brand.default',
                    color: already ? 'fg.muted' : 'fg.onAccent',
                  })}
                >
                  {already ? '담김' : '담기'}
                </button>
              </div>
            )
          })}
        </div>
      </form>

      {error && (
        <p role="alert" className={css({ textStyle: 'caption', color: 'danger.500' })}>
          {error}
        </p>
      )}

      <a
        href={`/collections/${collection.slug}`}
        className={css({
          textStyle: 'caption',
          color: 'brand.default',
          textDecoration: 'underline',
        })}
      >
        공개 페이지 보기 →
      </a>
    </div>
  )
}

function collectionWithout(collection: CollectionResponse, contentId: number): CollectionResponse {
  const items = collection.items
    .filter((item) => item.content.id !== contentId)
    .map((item, index) => ({ ...item, position: index }))
  return { ...collection, items, itemCount: items.length }
}

const inputBase = {
  px: '3',
  py: '2.5',
  rounded: 'md',
  textStyle: 'body',
  bg: 'bg.surface',
  color: 'fg.default',
  borderWidth: '1px',
  borderStyle: 'solid',
  borderColor: 'border.default',
} as const

const inputStyle = css(inputBase)

const itemRow = flex({
  gap: '2',
  alignItems: 'center',
  p: '3',
  rounded: 'md',
  bg: 'bg.surface',
  borderWidth: '1px',
  borderStyle: 'solid',
  borderColor: 'border.default',
})

const iconButton = css({
  textStyle: 'caption',
  px: '2',
  py: '1',
  rounded: 'sm',
  cursor: 'pointer',
  bg: 'bg.subtle',
  color: 'fg.default',
  _disabled: { opacity: 0.4, cursor: 'not-allowed' },
})

const primaryButton = css({
  textStyle: 'body',
  fontWeight: '600',
  px: '4',
  py: '2.5',
  rounded: 'md',
  cursor: 'pointer',
  bg: 'brand.default',
  color: 'fg.onAccent',
  _disabled: { opacity: 0.6, cursor: 'not-allowed' },
})

const chipStyle = (selected: boolean) =>
  css({
    textStyle: 'caption',
    px: '3',
    py: '1.5',
    rounded: 'full',
    cursor: 'pointer',
    borderWidth: '1px',
    borderStyle: 'solid',
    borderColor: selected ? 'brand.default' : 'border.default',
    bg: selected ? 'brand.default' : 'bg.surface',
    color: selected ? 'fg.onAccent' : 'fg.muted',
  })
