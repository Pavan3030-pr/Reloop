import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The backend runs on :8080 by default. Proxying keeps the browser on one origin,
// so the API's CORS allow-list and cookie-free bearer tokens behave identically
// in development and in a reverse-proxied production deployment.
const backend = process.env.BACKEND_URL ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: backend, changeOrigin: true },
      '/uploads': { target: backend, changeOrigin: true },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
  },
});
