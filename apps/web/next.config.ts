import type { NextConfig } from 'next'

const nextConfig: NextConfig = {
  reactStrictMode: true,

  // Docker 이미지에 node_modules 전체 대신 실제 사용 모듈만 포함한다 (docs/06-infra.md)
  output: 'standalone',

  // 모노레포 루트 기준으로 standalone 트레이싱
  outputFileTracingRoot: new URL('../../', import.meta.url).pathname,

  transpilePackages: ['@litmood/ui', '@litmood/api-client'],

  images: {
    // 외부 provider 의 커버 이미지 호스트 (ADR-010)
    remotePatterns: [
      { protocol: 'https', hostname: 'shopping-phinf.pstatic.net' }, // 네이버 책
      { protocol: 'https', hostname: 'ssl.pstatic.net' },
      { protocol: 'https', hostname: 'image.tmdb.org' }, // TMDB
      { protocol: 'https', hostname: 'i.scdn.co' }, // Spotify
      { protocol: 'http', hostname: 'localhost', port: '9000' }, // MinIO (로컬)
    ],
  },

  eslint: {
    // 린트는 CI 의 별도 잡에서 수행한다 (turbo run lint) — 빌드 시간을 늘리지 않는다
    ignoreDuringBuilds: true,
  },
}

export default nextConfig
