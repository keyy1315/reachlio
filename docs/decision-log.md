# 개인 — 1인 창업가용 통합 마케팅 허브(가칭 Marketing Hub) 기획 검토 · 네이버 연동 조사

- 날짜: 2026-11-12
- 기획서: `C:\Users\USER\Downloads\indie_marketing_hub_plan.md` (v1.0), `indie_marketing_hub_plan_v1.1.md` (v1.1)

## 1. v1.0 리뷰 요지
- 출발점(GSC·네이버 서치어드바이저·Bing 왕복 불편)은 실제 문제이고, 네이버는 해외 툴이 안 다뤄 빈자리 있음.
- 위험: "범위를 축소하지 않는다" 원칙(SEO+SNS+GEO+전환 = 회사 4개 분량), 통합만으로는 차별화 약함, 저가(4,900~9,900원) 대비 AI·크롤링 원가, 인디 개발자 지불의사 낮음, 네이버 공식 API 부재, Meta 앱 심사(비즈니스 인증).
- 제안: 1단계를 "검색엔진 3사 통합"으로 좁히고 인터뷰 먼저.

## 2. v1.1 리뷰 요지 (1단계 = Google·Naver·Bing 통합으로 축소, 네이버 릴리스 게이트 추가 → 개선됨)
- 남은 문제: 1단계 차별점이 네이버 자동수집 하나에 걸림 → 막히면 GSC+Bing만 남아 무료 툴과 차이 없음. **크롤링 없는 네이버 기능을 1단계 기본값으로** 둘 것.
- 0단계 맨 앞에 네이버 고객센터 서면 문의.
- 134행 "방문·가입 전환 이벤트"는 2단계(UTM/SNS)로 이동.
- 인터뷰를 PoC와 병행, 5장 차별화에 "네이버 포함 한국형 검색엔진 통합" 명시, 1단계 데이터 모델에서 SocialPost/AIQuery 등 제외.

## 3. 네이버 연동 조사 결과 (공식 사이트 직접 접근 불가 → 2차 자료 기준, 원문 미확인)
### 3-1. 계정 연결·열람 권한 위임
- GSC식 "사용자 추가/권한 위임" 기능 근거 **없음**. 네이버 로그인 OAuth 범위에 서치어드바이저 데이터 없음.
- 가능성 있는 방식: 서비스 측 네이버 계정으로 사용자 사이트 등록 → 사용자가 서비스용 소유확인 메타태그 설치(비밀번호 수집 X). **동일 사이트 다중 계정 소유확인 가능 여부는 미확인** → 계정 2개로 직접 테스트 필요. 소유확인 1년마다 갱신.
### 3-2. 자동 로그인·크롤링
- 네이버 이용약관은 "자동화된 프로그램 등을 이용한 게시물 등의 무단 수집" 금지(2차 자료), 위반 시 민사책임 가능.
- 위임 계정이라도 자동 로그인 후 리포트 수집은 해당 조항에 걸림 + 캡차/보안 확인으로 운영 불안정 → **브라우저 자동수집 PoC는 하지 않는 것으로 권고**(서면 예외 허용 없으면 상용 불가).
### 3-3. 이전 제안 정정
- "네이버 검색 API(웹문서)로 색인 여부 확인" 제안 → **2026-09-07 시행 검색 API 특약 개정**(보도 기준)으로 어려움: 네이버 검색결과 제공 목적으로만 이용, 서버 보관 최대 21일, API HUB(네이버클라우드) 이관 중·개발자센터 신규 신청 중단.
### 3-4. 약관 문제 없는 네이버 기능 (1단계 현실적 범위)
- IndexNow URL 제출(네이버 공식 지원), 사이트맵·robots·메타태그 점검, 리포트 수동 입력/파일 업로드(다운로드 기능 존재 여부 미확인), 서치어드바이저 바로가기.
- 결론: 현재 네이버 통계 자동수집은 불가, 1단계 네이버 기능은 "제출·점검" 수준 → 이 범위로 지불의사 있는지 인터뷰로 검증.

## 4. 사용자 직접 확인할 것
1. 네이버 계정 2개로 같은 사이트 소유확인 가능 여부 테스트
2. 서치어드바이저 고객센터에 위임 계정 리포트 자동 조회 허용 여부 서면 문의
3. 네이버 이용약관·검색 API 특약 원문 확인

## 5. 개발 착수 결정 (2026-11-12~13)
- 네이버: 크롤러 검토 → 최종 계획은 "Google·Bing 먼저, 네이버는 정상 열람·내보낸 샘플로 파싱/적재, 승인 경로 확보 시 정기수집". 크롤러는 보류.
- 스택 결정 과정: Next.js 단독 ↔ Spring Boot 비교 → Thymeleaf 거부, React CSR 거부(SEO) → **Spring Boot(API+수집+정적서빙, 서버 1개) + React Router v8 정적빌드(`ssr:false`, 홍보페이지 `prerender`)**. 네이버 크롤러 JS 렌더링 약해 프리렌더 필요.
- 생성물: `D:\dev\marketing-hub\`
  - `web/` React Router v8 + TS + Tailwind. Node 서버(`@react-router/serve`, Dockerfile, start 스크립트) 제거. vite `/api` → `localhost:8080` 프록시. `npm run build` → `build/client/index.html`(프리렌더) + `__spa-fallback.html` 확인.
  - `docker-compose.yml` Postgres 17 (marketing_hub / hub / hub, 5432) — 아직 미기동.
  - `api/` 미생성 (Spring Boot, Gradle, JDK 25 예정).
- JDK 25: 사용자 폴더에 zip 해제만 (시스템 JAVA_HOME 은 변경하지 않음).
- 설계 초안: 테이블 `users / site / connection(인증정보 암호화) / search_metric(엔진·출처 API|UPLOAD, upsert) / collect_run`. 엔진별 수집기 3개 공통 인터페이스, `@Scheduled` 일 1회 최근 며칠 재수집(GSC 2~3일 지연).
- 미결: 로그인 방식(Google 로그인+GSC scope 추천), Bing 인증(API 키 추천), 네이버 내보내기 형식 확인, URL 색인 점검 1단계 포함 여부, 배포 위치.

## 6. 2026-11-13 진행
- 기획서 v1.2 작성: `C:\Users\USER\Downloads\indie_marketing_hub_plan_v1.2.md` (스택·네이버 방식·데이터 모델·진행 순서 반영). DB 스키마 `D:\dev\marketing-hub\db\schema.sql` 최소 버전으로 확정.
- 서치어드바이저 리포트: 노출/클릭 **그래프 데이터를 xls·csv·png로 출력 가능**(사용자 확인). 그래프 기준이라 일별 합계일 가능성 높음 → `search_metric`의 `('', '')` 행에 해당. 검색어별/페이지별 제공 여부는 샘플 받아 확인 필요.
- 네이버 이용약관 원문 확인 시도: policy.naver.com 은 도구에서 접근 차단, 검색으로도 원문 미확보 → 사용자가 직접 확인하기로. 확인할 부분: 금지행위 조항의 "자동화된 프로그램/수단" 문구, 서치어드바이저 별도 약관 유무.
- 남은 작업(다음 날 이후): api 생성, Postgres 기동, Flyway 적용, 정적 서빙 설정, Google Cloud OAuth 설정.

## 참고 출처
- https://www.innoforest.co.kr/report/NS00000366/
- https://www.lawtalk.co.kr/qna/654910
- https://www.tokenpost.kr/news/ai/403521
- https://brunch.co.kr/@ainote/123
- https://imweb.me/faq?mode=view&category=29&category2=35&idx=71780
- https://developers.google.com/search/blog/2018/08/collaboration-and-user-management-in?hl=ko
- https://news.hada.io/topic?id=19225
