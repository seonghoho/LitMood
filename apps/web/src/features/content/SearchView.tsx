'use client'

import { useEffect, useMemo, useRef, useState } from 'react'
import { css } from 'styled-system/css'
import { flex, stack } from 'styled-system/patterns'
import { ApiError } from '@litmood/api-client'
import { apiGet } from '@/shared/lib/api'
import { ContentCard } from './ContentCard'
import {
  CONTENT_TYPE_LABEL,
  CONTENT_TYPES,
  PROVIDER_CONTENT_LABEL,
  type ContentType,
  type SearchResponse,
} from './types'

/**
 * F-02-01 — 통합 검색.
 *
 * 검색은 입력마다 결과가 바뀌므로 클라이언트에서 수행한다
 * (docs/03-architecture.md 의 화면 유형별 페칭 규칙).
 * 응답 캐싱은 서버가 담당하므로 여기서는 캐시를 두지 않는다.
 */
export function SearchView() {
  const [query, setQuery] = useState('')
  const [activeType, setActiveType] = useState<ContentType>('BOOK')
  const [data, setData] = useState<SearchResponse | null>(null)
  const [status, setStatus] = useState<'idle' | 'loading' | 'error'>('idle')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  // 입력 중 매 글자마다 호출하면 외부 API rate limit 을 태운다
  const debounced = useDebounced(query, 400)
  // 늦게 도착한 이전 요청이 최신 결과를 덮어쓰지 않도록 순번을 센다
  const requestId = useRef(0)

  useEffect(() => {
    const trimmed = debounced.trim()
    if (trimmed.length < 2) {
      setData(null)
      setStatus('idle')
      return
    }

    const id = ++requestId.current
    const controller = new AbortController()
    setStatus('loading')
    setErrorMessage(null)

    apiGet<SearchResponse>(`/api/v1/contents/search?q=${encodeURIComponent(trimmed)}`, {
      signal: controller.signal,
    })
      .then((response) => {
        if (id !== requestId.current) return
        setData(response)
        setStatus('idle')
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted || id !== requestId.current) return
        setStatus('error')
        setErrorMessage(
          error instanceof ApiError ? error.problem.title : '검색 중 문제가 발생했습니다',
        )
      })

    return () => controller.abort()
  }, [debounced])

  const counts = useMemo(() => {
    const entries = CONTENT_TYPES.map((type) => [type, data?.results?.[type]?.length ?? 0] as const)
    return Object.fromEntries(entries) as Record<ContentType, number>
  }, [data])

  const items = data?.results?.[activeType] ?? []
  const failedLabels = (data?.failedProviders ?? []).map((p) => PROVIDER_CONTENT_LABEL[p])

  return (
    <div className={stack({ gap: '5' })}>
      <label className={stack({ gap: '2' })}>
        <span className={css({ textStyle: 'caption', color: 'fg.muted' })}>
          책·영화·음악을 한 번에 검색합니다
        </span>
        <input
          type="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="제목, 작가, 아티스트로 검색"
          className={css({
            w: 'full',
            px: '4',
            py: '3',
            rounded: 'md',
            textStyle: 'body',
            bg: 'bg.surface',
            color: 'fg.default',
            borderWidth: '1px',
            borderStyle: 'solid',
            borderColor: 'border.default',
            _focusVisible: {
              outlineWidth: '2px',
              outlineStyle: 'solid',
              outlineColor: 'brand.default',
            },
          })}
        />
      </label>

      {/* 탭: 결과 건수를 함께 보여줘 빈 탭을 헛클릭하지 않게 한다 */}
      <div role="tablist" className={flex({ gap: '1' })}>
        {CONTENT_TYPES.map((type) => {
          const selected = type === activeType
          return (
            <button
              key={type}
              role="tab"
              type="button"
              aria-selected={selected}
              onClick={() => setActiveType(type)}
              className={css({
                textStyle: 'caption',
                px: '4',
                py: '2',
                rounded: 'full',
                cursor: 'pointer',
                borderWidth: '1px',
                borderStyle: 'solid',
                borderColor: selected ? 'brand.default' : 'border.default',
                bg: selected ? 'brand.default' : 'bg.surface',
                color: selected ? 'fg.onAccent' : 'fg.muted',
                _focusVisible: {
                  outlineWidth: '2px',
                  outlineStyle: 'solid',
                  outlineColor: 'brand.default',
                  outlineOffset: '2px',
                },
              })}
            >
              {CONTENT_TYPE_LABEL[type]}
              {data ? ` ${counts[type]}` : ''}
            </button>
          )
        })}
      </div>

      {/* 부분 실패는 숨기지 않고 알린다 (NFR-03) */}
      {failedLabels.length > 0 && (
        <p
          role="status"
          className={css({
            textStyle: 'caption',
            color: 'fg.muted',
            bg: 'bg.subtle',
            px: '3',
            py: '2',
            rounded: 'sm',
          })}
        >
          {failedLabels.join('·')} 검색을 일시적으로 불러오지 못했습니다. 잠시 후 다시 시도해
          주세요.
        </p>
      )}

      <div aria-live="polite" className={stack({ gap: '2' })}>
        {status === 'loading' && (
          <p className={css({ textStyle: 'caption', color: 'fg.muted' })}>검색 중…</p>
        )}
        {status === 'error' && (
          <p className={css({ textStyle: 'body', color: 'fg.default' })}>{errorMessage}</p>
        )}
        {status === 'idle' && data && items.length === 0 && (
          <p className={css({ textStyle: 'caption', color: 'fg.muted' })}>
            {CONTENT_TYPE_LABEL[activeType]} 결과가 없습니다.
          </p>
        )}
        {items.map((content) => (
          <ContentCard key={`${content.provider}:${content.externalId}`} content={content} />
        ))}
      </div>
    </div>
  )
}

function useDebounced<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value)

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs)
    return () => clearTimeout(timer)
  }, [value, delayMs])

  return debounced
}
