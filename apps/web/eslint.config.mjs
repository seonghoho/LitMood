import baseConfig from '@litmood/config/eslint'

export default [
  ...baseConfig,
  {
    ignores: [
      'styled-system/**', // Panda 코드젠 산출물
      '.next/**',
      'next-env.d.ts', // Next 가 생성·관리하는 파일
      '*.cjs', // PostCSS 등 CommonJS 설정
    ],
  },
]
