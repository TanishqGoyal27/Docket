import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The build goes straight into Spring Boot's static folder, so one Java app serves everything.
// During `npm run dev`, API calls are proxied to the Spring Boot server on port 8080.
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080' },
  },
});
