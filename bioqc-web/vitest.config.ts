import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

/**
 * Configuracao dedicada ao vitest. Mantem a vite.config.ts limpa do proxy
 * do dev server e evita carregar o plugin tailwind que so faz sentido em runtime.
 */
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    css: false,
  },
})
