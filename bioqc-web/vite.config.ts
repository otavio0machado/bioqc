import tailwindcss from '@tailwindcss/vite'
import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

function resolveApiProxyTarget(env: Record<string, string>) {
  if (env.VITE_API_PROXY_TARGET) {
    return env.VITE_API_PROXY_TARGET
  }

  if (env.VITE_API_URL) {
    try {
      return new URL(env.VITE_API_URL).origin
    } catch {
      // Ignore invalid absolute URLs and use the local fallback below.
    }
  }

  return 'http://localhost:8080'
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [react(), tailwindcss()],
    server: {
      proxy: {
        '/api': {
          target: resolveApiProxyTarget(env),
          changeOrigin: true,
        },
      },
    },
  }
})
