// Panda CSS 는 PostCSS 단계에서 사용된 스타일만 추출해 CSS 를 생성한다 (ADR-002).
module.exports = {
  plugins: {
    '@pandacss/dev/postcss': {},
  },
}
