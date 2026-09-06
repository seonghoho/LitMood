'use client'

import { useRouter } from 'next/navigation'
import { useState } from 'react'
import { css } from 'styled-system/css'
import { flex, stack } from 'styled-system/patterns'
import { ApiError } from '@litmood/api-client'
import type { ContentSummary } from '@/features/content/types'
import { VISIBILITY_LABEL, type Visibility } from '@/features/record/types'
import { apiDelete, apiPatch, apiPost } from '@/shared/lib/api'
import {
  buildCollectionPatch,
  buildCoverPatch,
  selectedCoverUrl,
  toDraft,
  type CollectionPatch,
} from './collection-edit'
import type { CollectionItem, CollectionResponse } from './types'

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

/**
 * 컬렉션 편집 — 메타·커버·콘텐츠·노트 (F-05-02, F-05-03).
 *
 * 순서는 위/아래 버튼으로 바꾼다. 드래그를 붙이지 않은 것은 미완이 아니라 선택이다 —
 * 키보드만으로 조작할 수 있어야 하고(NFR-05), 터치 드래그는 스크롤과 충돌한다.
 * 아이템이 스물을 넘기 시작하면 그때 다시 본다 (이슈 #7).
 */
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

  const patchCollection = (patch: CollectionPatch) =>
    run(() => apiPatch<CollectionResponse>(`/api/v1/collections/${collection.slug}`, patch))

  const addItem = (content: ContentSummary, note: string) =>
    run(() =>
      apiPost<CollectionResponse>(`/api/v1/collections/${collection.slug}/items`, {
        provider: content.provider,
        externalId: content.externalId,
        note: note.trim() || null,
      }),
    )

  const removeItem = (contentId: number) =>
    run(() =>
      apiDelete(`/api/v1/collections/${collection.slug}/items/${contentId}`).then(() =>
        collectionWithout(collection, contentId),
      ),
    )

  /** 담은 이유는 나중에도 고칠 수 있다 (F-05-03). 빈 문자열이 지움이다. */
  const saveNote = (contentId: number, note: string) =>
    run(() =>
      apiPatch<CollectionResponse>(`/api/v1/collections/${collection.slug}/items/${contentId}`, {
        note,
      }),
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

  const selectedCover = selectedCoverUrl(collection)
  const coverCandidates = collection.items.filter((item) => item.content.coverUrl)

  return (
    <div className={stack({ gap: '6' })}>
      <MetaForm collection={collection} busy={busy} onSave={patchCollection} />

      {coverCandidates.length > 0 && (
        <div className={stack({ gap: '3' })}>
          <h2 className={css({ textStyle: 'title' })}>대표 이미지</h2>
          <p className={css({ textStyle: 'caption', color: 'fg.muted' })}>
            공유 링크와 목록에 쓰입니다. 고르지 않으면 첫 아이템의 표지를 따라갑니다.
          </p>
          <div className={flex({ gap: '2', flexWrap: 'wrap', alignItems: 'stretch' })}>
            <button
              type="button"
              onClick={() => void patchCollection(buildCoverPatch(null))}
              disabled={busy || selectedCover === null}
              aria-pressed={selectedCover === null}
              className={coverOption(selectedCover === null)}
            >
              자동
            </button>
            {coverCandidates.map((item) => {
              const url = item.content.coverUrl!
              const chosen = selectedCover === url
              return (
                <button
                  key={item.content.id}
                  type="button"
                  onClick={() => void patchCollection(buildCoverPatch(url))}
                  disabled={busy || chosen}
                  aria-pressed={chosen}
                  aria-label={`${item.content.title} 표지를 대표 이미지로`}
                  className={coverOption(chosen)}
                >
                  {/* 리셋의 img { height: auto } 가 유틸리티를 이기므로 크기를 인라인으로 못박는다 */}
                  <img
                    src={url}
                    alt=""
                    style={{
                      width: '44px',
                      height: '62px',
                      objectFit: 'cover',
                      borderRadius: '4px',
                    }}
                  />
                </button>
              )
            })}
          </div>
        </div>
      )}

      <div className={stack({ gap: '3' })}>
        <h2 className={css({ textStyle: 'title' })}>담긴 콘텐츠 {collection.itemCount}</h2>
        {collection.items.length === 0 && (
          <p className={css({ textStyle: 'caption', color: 'fg.muted' })}>
            아래에서 검색해 콘텐츠를 담아 보세요.
          </p>
        )}
        <ol className={stack({ gap: '2' })}>
          {collection.items.map((item, index) => (
            <li key={item.content.id} className={stack({ ...itemBox, gap: '2' })}>
              <div className={flex({ gap: '2', alignItems: 'center' })}>
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
              </div>
              <ItemNote item={item} busy={busy} onSave={saveNote} />
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
          {results.map((content) => (
            <SearchResultRow
              key={`${content.provider}:${content.externalId}`}
              content={content}
              already={collection.items.some(
                (item) => item.content.externalId === content.externalId,
              )}
              busy={busy}
              onAdd={addItem}
            />
          ))}
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

/**
 * 제목·설명·공개 범위 (F-05-01).
 *
 * 달라진 것만 보낸다 — 바뀌지 않은 값까지 실어 보내면 다른 탭에서 방금 고친 값을
 * 되돌려 덮어쓴다. 저장 뒤에는 패치가 비어 버튼이 스스로 잠긴다.
 */
function MetaForm({
  collection,
  busy,
  onSave,
}: {
  collection: CollectionResponse
  busy: boolean
  onSave: (patch: CollectionPatch) => Promise<void>
}) {
  const [draft, setDraft] = useState(() => toDraft(collection))
  const patch = buildCollectionPatch(collection, draft)
  const dirty = Object.keys(patch).length > 0

  return (
    <div className={stack({ gap: '3' })}>
      <h2 className={css({ textStyle: 'title' })}>컬렉션 정보</h2>

      <label className={stack({ gap: '1.5' })}>
        <span className={css({ textStyle: 'caption', color: 'fg.muted' })}>제목</span>
        <input
          value={draft.title}
          onChange={(event) => setDraft({ ...draft, title: event.target.value })}
          maxLength={100}
          className={inputStyle}
        />
      </label>

      <label className={stack({ gap: '1.5' })}>
        <span className={css({ textStyle: 'caption', color: 'fg.muted' })}>설명</span>
        <textarea
          value={draft.description}
          onChange={(event) => setDraft({ ...draft, description: event.target.value })}
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
              onClick={() => setDraft({ ...draft, visibility: v })}
              aria-pressed={v === draft.visibility}
              className={chipStyle(v === draft.visibility)}
            >
              {VISIBILITY_LABEL[v]}
            </button>
          ))}
        </div>
      </div>

      <div className={flex({ gap: '2', alignItems: 'center' })}>
        <button
          type="button"
          onClick={() => void onSave(patch)}
          disabled={busy || !dirty}
          className={primaryButton}
        >
          저장
        </button>
        {/* 슬러그는 제목에서 한 번 만들어지고 바뀌지 않는다 — 공유된 링크가 깨지면 안 된다 */}
        <span className={css({ textStyle: 'caption', color: 'fg.muted' })}>
          제목을 고쳐도 공유 주소(/{collection.slug})는 그대로입니다
        </span>
      </div>
    </div>
  )
}

/** 담은 이유 — 담을 때뿐 아니라 나중에도 고친다 (F-05-03). */
function ItemNote({
  item,
  busy,
  onSave,
}: {
  item: CollectionItem
  busy: boolean
  onSave: (contentId: number, note: string) => Promise<void>
}) {
  const [note, setNote] = useState(item.note ?? '')
  // 저장되면 서버 값이 따라와 dirty 가 스스로 풀린다
  const dirty = note.trim() !== (item.note ?? '')

  return (
    <div className={flex({ gap: '2', alignItems: 'center', pl: '7' })}>
      <input
        value={note}
        onChange={(event) => setNote(event.target.value)}
        placeholder="담은 이유 (선택)"
        maxLength={300}
        aria-label={`${item.content.title} 담은 이유`}
        className={css({ ...inputBase, flex: 1, textStyle: 'caption', py: '1.5' })}
      />
      <button
        type="button"
        onClick={() => void onSave(item.content.id, note.trim())}
        disabled={busy || !dirty}
        className={iconButton}
      >
        {note.trim() === '' && item.note ? '지우기' : '저장'}
      </button>
    </div>
  )
}

/** 검색 결과 한 줄 — 담으면서 이유를 함께 남길 수 있다. */
function SearchResultRow({
  content,
  already,
  busy,
  onAdd,
}: {
  content: ContentSummary
  already: boolean
  busy: boolean
  onAdd: (content: ContentSummary, note: string) => Promise<void>
}) {
  const [note, setNote] = useState('')

  return (
    <div className={stack({ ...itemBox, gap: '2' })}>
      <div className={flex({ gap: '2', alignItems: 'center' })}>
        <span className={css({ textStyle: 'body', flex: 1, color: 'fg.default' })}>
          {content.title}
        </span>
        <button
          type="button"
          onClick={() => void onAdd(content, note)}
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
      {!already && (
        <input
          value={note}
          onChange={(event) => setNote(event.target.value)}
          placeholder="담은 이유 (선택)"
          maxLength={300}
          aria-label={`${content.title} 담은 이유`}
          className={css({ ...inputBase, textStyle: 'caption', py: '1.5' })}
        />
      )}
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

const itemBox = {
  p: '3',
  rounded: 'md',
  bg: 'bg.surface',
  borderWidth: '1px',
  borderStyle: 'solid',
  borderColor: 'border.default',
} as const

const coverOption = (selected: boolean) =>
  css({
    p: '1.5',
    rounded: 'md',
    cursor: 'pointer',
    textStyle: 'caption',
    color: 'fg.default',
    bg: 'bg.surface',
    borderWidth: '2px',
    borderStyle: 'solid',
    borderColor: selected ? 'brand.default' : 'border.default',
    _disabled: { cursor: 'default' },
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
