import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    // Built straight into Spring Boot's static resources folder, so the backend serves this app
    // itself at http://localhost:8080/ - "server up" means "the app is up", no separate process.
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
})
