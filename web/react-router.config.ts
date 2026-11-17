import type { Config } from "@react-router/dev/config";

export default {
  // 런타임 Node 서버 없음: build/client 정적 파일을 Spring Boot가 서빙한다.
  ssr: false,
  // 홍보 페이지(SEO 대상)만 빌드 시 HTML로 생성. 나머지(대시보드)는 SPA fallback.
  prerender: ["/"],
} satisfies Config;
