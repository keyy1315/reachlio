import { reactRouter } from "@react-router/dev/vite";
import tailwindcss from "@tailwindcss/vite";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [tailwindcss(), reactRouter()],
  resolve: {
    tsconfigPaths: true,
  },
  // 개발 중 /api 는 Spring Boot(8080)로 넘긴다 → CORS 설정 불필요
  server: {
    proxy: { "/api": "http://localhost:8080" },
  },
});
