import { defineConfig } from 'orval'

/**
 * ADR-008 — OpenAPI 를 단일 진실 원천으로 삼는다.
 * Spring 컨트롤러에서 생성된 스펙으로부터 TS 타입과 TanStack Query 훅을 파생시켜,
 * 프론트/백 언어 이원화로 인한 계약 불일치를 컴파일 타임에 잡는다.
 *
 * 사용법:
 *   1) apps/api 기동
 *   2) pnpm --filter @litmood/api-client fetch-spec
 *   3) pnpm --filter @litmood/api-client codegen
 */
export default defineConfig({
  litmood: {
    input: './openapi.yaml',
    output: {
      mode: 'tags-split',
      target: './src/generated/endpoints.ts',
      schemas: './src/generated/model',
      client: 'react-query',
      httpClient: 'fetch',
      clean: true,
      prettier: true,
      override: {
        mutator: {
          path: './src/fetcher.ts',
          name: 'apiFetcher',
        },
        query: {
          useQuery: true,
          useInfinite: true,
          // 타임라인·피드는 커서 기반 무한 스크롤이다 (docs/05-api-spec.md)
          useInfiniteQueryParam: 'cursor',
        },
      },
    },
  },
})
