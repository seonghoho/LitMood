import { defineConfig } from 'orval'

/**
 * ADR-008 — OpenAPI 를 단일 진실 원천으로 삼는다.
 * Spring 컨트롤러에서 생성된 스펙으로부터 TS 타입을 파생시켜,
 * 프론트/백 언어 이원화로 인한 계약 불일치를 컴파일 타임에 잡는다.
 *
 * 사용법:
 *   1) apps/api 기동
 *   2) pnpm --filter @litmood/api-client fetch-spec
 *   3) pnpm --filter @litmood/api-client codegen
 *
 * TanStack Query 훅은 아직 생성하지 않는다 (client: 'fetch'). 이유:
 *  - 앱에 QueryClientProvider 가 없어 훅을 쓸 자리가 없다.
 *  - orval 7.21 이 UseInfiniteQueryOptions 에 제네릭 인자를 6개 넘기지만
 *    설치된 @tanstack/react-query 5.102 는 5개까지만 받는다 (TS2707).
 *  - useInfinite 는 전역 설정이라 cursor 파라미터가 없는
 *    /moods/{name}/contents 에도 커서 페이징 코드를 만들어 컴파일이 깨진다.
 * 훅을 도입하는 이슈에서 위 세 가지를 함께 정리하고 켠다.
 *
 * 생성되는 fetch 함수는 operationId 를 컨트롤러 메서드명에서 받으므로
 * 태그 간에 겹치는 이름에 orval 이 번호를 붙인다 (create1, get1, update1).
 * 호출부를 생성 함수로 옮길 때 @Operation(operationId = ...) 로 먼저 정리할 것.
 */
export default defineConfig({
  litmood: {
    input: './openapi.yaml',
    output: {
      mode: 'tags-split',
      target: './src/generated/endpoints.ts',
      schemas: './src/generated/model',
      client: 'fetch',
      httpClient: 'fetch',
      clean: true,
      prettier: true,
      override: {
        mutator: {
          path: './src/fetcher.ts',
          name: 'apiFetcher',
        },
      },
    },
  },
})
