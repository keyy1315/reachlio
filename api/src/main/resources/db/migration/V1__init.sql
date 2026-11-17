-- marketing-hub 1단계 스키마 (PostgreSQL 17)

-- 사용자: Google 로그인 기준
CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    google_sub  TEXT NOT NULL UNIQUE,          -- Google OAuth subject (불변 ID)
    email       TEXT NOT NULL,
    name        TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 엔진 인증정보: 사용자 단위 (Google 계정 하나 / Bing API 키 하나로 여러 사이트 조회)
CREATE TABLE credential (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    engine      TEXT NOT NULL CHECK (engine IN ('GOOGLE', 'BING')),
    secret_enc  TEXT NOT NULL,                 -- 앱에서 AES-GCM 암호화 (Google refresh token / Bing API key)
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, engine)
);

-- 관리 대상 사이트
CREATE TABLE site (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    domain        TEXT NOT NULL,               -- 정규화된 호스트 (예: example.com)
    indexnow_key  TEXT,                        -- 사이트에 공개되는 키라 암호화 불필요
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, domain)
);

-- 사이트 ↔ 엔진 연결: 엔진 쪽 사이트 식별자
CREATE TABLE connection (
    id          BIGSERIAL PRIMARY KEY,
    site_id     BIGINT NOT NULL REFERENCES site(id) ON DELETE CASCADE,
    engine      TEXT NOT NULL CHECK (engine IN ('GOOGLE', 'BING', 'NAVER')),
    property    TEXT NOT NULL,                 -- GSC: 'sc-domain:example.com' 또는 'https://example.com/', Bing: siteUrl, Naver: 서치어드바이저 등록 URL
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (site_id, engine)
);

-- 검색 성과: 세 엔진 공통
-- query/page 빈 문자열('') 규칙:
--   ('', '')      = 일별 전체 합계  ← GSC 는 익명 검색어가 빠지므로 검색어 행 합계 ≠ 전체. 합계를 따로 저장한다.
--   (검색어, '')  = 검색어별
--   ('', 페이지)  = 페이지별
-- CTR 은 clicks / impressions 로 계산 (저장 안 함)
CREATE TABLE search_metric (
    id             BIGSERIAL PRIMARY KEY,
    connection_id  BIGINT NOT NULL REFERENCES connection(id) ON DELETE CASCADE,
    metric_date    DATE NOT NULL,              -- 엔진 기준 날짜 (GSC 는 미국 태평양 시간)
    query          TEXT NOT NULL DEFAULT '',
    page           TEXT NOT NULL DEFAULT '',
    impressions    INTEGER NOT NULL,
    clicks         INTEGER NOT NULL,
    position       NUMERIC(6, 2),              -- 평균 순위 (합산 불가). 엔진이 안 주면 NULL
    source         TEXT NOT NULL CHECK (source IN ('API', 'UPLOAD')),
    collected_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (connection_id, metric_date, query, page)   -- 재수집 시 ON CONFLICT DO UPDATE
);

-- 수집 실행 이력 (API 수집, 네이버 업로드 모두)
CREATE TABLE collect_run (
    id             BIGSERIAL PRIMARY KEY,
    connection_id  BIGINT NOT NULL REFERENCES connection(id) ON DELETE CASCADE,
    started_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at    TIMESTAMPTZ,
    status         TEXT NOT NULL CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED')),
    date_from      DATE,
    date_to        DATE,
    rows_upserted  INTEGER,
    error          TEXT
);
CREATE INDEX collect_run_connection_started ON collect_run (connection_id, started_at DESC);
