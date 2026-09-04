/**
 * 로컬 개발용 외부 provider 스텁.
 *
 * 네이버·TMDB·Spotify API 키가 없어도 전체 스택을 굴릴 수 있게 한다.
 * 세 provider 의 응답 형태를 그대로 흉내 내므로, 백엔드의 정규화 로직
 * (태그 제거, '^' 저자 분리, 날짜 정밀도 처리)이 실제와 같은 경로를 탄다.
 *
 * 사용법:
 *   pnpm stub          # 이 서버를 9876 포트로 기동
 *   pnpm api:dev:stub  # 백엔드를 스텁에 물려 기동
 *
 * 장애 시나리오 재현:
 *   FAIL=movie pnpm stub     # 영화 provider 만 500 을 반환 (부분 실패 확인)
 *   DELAY=4000 pnpm stub     # 모든 응답을 4초 지연 (타임아웃 확인)
 */
import { createServer } from 'node:http'

const PORT = Number(process.env.STUB_PORT ?? 9876)
const DELAY = Number(process.env.DELAY ?? 0)
const FAIL = (process.env.FAIL ?? '').split(',').filter(Boolean) // book | movie | music

const BOOKS = [
  {
    // 검색어 강조 <b> 태그와 '^' 로 구분된 복수 저자 — 실제 네이버 응답의 특징
    title: '<b>노르웨이의</b> 숲',
    author: '무라카미 하루키^양억관',
    isbn: '9788937473135',
    publisher: '민음사',
    pubdate: '20170825',
    image: 'https://picsum.photos/seed/norwegian/300/430',
    description: '스무 살의 이야기',
  },
  {
    title: '상실의 시대',
    author: '무라카미 하루키',
    isbn: '9788937434471',
    publisher: '문학사상사',
    pubdate: '20100115',
    image: 'https://picsum.photos/seed/loss/300/430',
    description: '구판 번역',
  },
  {
    title: '바다의 뚜껑',
    author: '요시모토 바나나',
    isbn: '9788954634397',
    publisher: '문학동네',
    pubdate: '20150601',
    image: 'https://picsum.photos/seed/sea/300/430',
    description: '여름의 이야기',
  },
  {
    title: '아무튼, 계속',
    author: '김교석',
    isbn: '9791189318116',
    publisher: '위고',
    pubdate: '20181120',
    image: 'https://picsum.photos/seed/keep/300/430',
    description: '계속하는 삶에 대하여',
  },
]

const MOVIES = [
  {
    id: 296,
    title: '상실의 시대',
    original_title: 'ノルウェイの森',
    release_date: '2010-12-11',
    poster_path: '/norwegian.jpg',
    overview: '와타나베의 스무 살',
    vote_average: 6.4,
  },
  {
    id: 500,
    title: '바닷마을 다이어리',
    original_title: '海街diary',
    release_date: '2015-06-13',
    poster_path: '/umimachi.jpg',
    overview: '네 자매의 여름',
    vote_average: 7.5,
  },
]

const TRACKS = [
  {
    id: '4uLU6hMCjMI75M1A2tKUQC',
    name: 'Norwegian Wood',
    duration_ms: 125000,
    artists: [{ name: 'The Beatles' }],
    album: {
      name: 'Rubber Soul',
      release_date: '1965-12-03',
      release_date_precision: 'day',
      images: [{ url: 'https://picsum.photos/seed/rubbersoul/300/300' }],
    },
    external_ids: { isrc: 'GBAYE0601696' },
    external_urls: { spotify: 'https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC' },
  },
  {
    id: '1301WleyT98MSxVHPZCA6M',
    name: 'Sunday Morning',
    duration_ms: 176000,
    artists: [{ name: 'The Velvet Underground' }, { name: 'Nico' }],
    album: {
      name: 'The Velvet Underground & Nico',
      // 정밀도가 'year' 인 경우 — 백엔드가 1월 1일로 보정하는지 확인용
      release_date: '1967',
      release_date_precision: 'year',
      images: [{ url: 'https://picsum.photos/seed/vu/300/300' }],
    },
    external_ids: { isrc: 'USPR36700101' },
    external_urls: { spotify: 'https://open.spotify.com/track/1301WleyT98MSxVHPZCA6M' },
  },
]

function send(res, body, status = 200) {
  const payload = JSON.stringify(body)
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(payload),
  })
  res.end(payload)
}

function handle(req, res) {
  let url
  try {
    url = new URL(req.url, `http://localhost:${PORT}`)
  } catch {
    // 인코딩되지 않은 한글이 그대로 온 경우 등 — 스텁이 죽지 않게 한다
    return send(res, { error: 'stub: malformed url' }, 400)
  }
  const { pathname, searchParams } = url

  // Spotify Client Credentials 토큰 교환
  if (req.method === 'POST') {
    return send(res, { access_token: 'stub-token', expires_in: 3600, token_type: 'Bearer' })
  }

  // ── 책 (네이버) ──────────────────────────────────────────
  if (pathname.startsWith('/v1/search/book')) {
    if (FAIL.includes('book')) return send(res, { errorMessage: 'stub failure' }, 500)

    const isbn = searchParams.get('d_isbn')
    if (isbn) {
      // 상세 조회 — 기록/컬렉션 생성 시 스냅샷을 만드는 경로
      const found = BOOKS.find((b) => b.isbn === isbn)
      return send(res, { items: found ? [found] : [] })
    }
    const query = (searchParams.get('query') ?? '').trim()
    const items = query ? BOOKS.filter((b) => matches(b.title, b.author, query)) : BOOKS
    return send(res, { items: items.length > 0 ? items : BOOKS })
  }

  // ── 영화 (TMDB) ─────────────────────────────────────────
  if (pathname === '/search/movie') {
    if (FAIL.includes('movie')) return send(res, { status_message: 'stub failure' }, 500)
    return send(res, { results: MOVIES })
  }
  const movieDetail = pathname.match(/^\/movie\/(\d+)$/)
  if (movieDetail) {
    const found = MOVIES.find((m) => String(m.id) === movieDetail[1])
    if (!found) return send(res, { success: false, status_message: 'Not found' }, 404)
    return send(res, {
      ...found,
      runtime: 133,
      credits: { crew: [{ job: 'Director', name: '트란 안 훙' }] },
    })
  }

  // ── 음악 (Spotify) ──────────────────────────────────────
  if (pathname === '/search') {
    if (FAIL.includes('music')) return send(res, { error: { message: 'stub failure' } }, 500)
    return send(res, { tracks: { items: TRACKS } })
  }
  const trackDetail = pathname.match(/^\/tracks\/(.+)$/)
  if (trackDetail) {
    const found = TRACKS.find((t) => t.id === trackDetail[1])
    if (!found) return send(res, { error: { message: 'Not found' } }, 404)
    return send(res, found)
  }

  send(res, { error: 'stub: unknown path ' + pathname }, 404)
}

/** 대충이라도 검색어를 반영해야 "검색이 동작한다"는 감각이 산다. */
function matches(title, author, query) {
  const haystack = `${title} ${author}`.replace(/<[^>]*>/g, '').toLowerCase()
  return query
    .toLowerCase()
    .split(/\s+/)
    .some((token) => haystack.includes(token))
}

createServer((req, res) => {
  if (DELAY > 0) {
    setTimeout(() => handle(req, res), DELAY)
  } else {
    handle(req, res)
  }
}).listen(PORT, '127.0.0.1', () => {
  console.log(`stub provider listening on http://localhost:${PORT}`)
  if (FAIL.length > 0) console.log(`  실패 시뮬레이션: ${FAIL.join(', ')}`)
  if (DELAY > 0) console.log(`  응답 지연: ${DELAY}ms`)
})
