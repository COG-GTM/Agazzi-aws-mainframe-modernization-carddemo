import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// Dev: `npm run mock` (Prism on :4010) or carddemo-app on :8080 -> CARDDEMO_API=http://localhost:8080 npm run dev
const target = process.env.CARDDEMO_API ?? 'http://localhost:4010';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target, changeOrigin: true },
      '/actuator': { target, changeOrigin: true },
    },
  },
  build: { outDir: 'dist', sourcemap: true },
});
