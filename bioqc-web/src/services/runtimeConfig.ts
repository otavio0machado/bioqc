type RuntimeConfig = {
  apiUrl?: string
  siteUrl?: string
}

declare global {
  interface Window {
    __APP_CONFIG__?: RuntimeConfig
  }
}

const PLACEHOLDER_API_HOSTS = ['seu-backend.up.railway.app']
const LOCAL_HOSTS = ['localhost', '127.0.0.1']

function isPlaceholderApiUrl(candidate: string) {
  return PLACEHOLDER_API_HOSTS.some((host) => candidate.includes(host))
}

function isLocalHost(hostname: string) {
  return LOCAL_HOSTS.includes(hostname)
}

export function normalizeConfiguredApiUrlForHostname(candidate: string | null | undefined, currentHostname: string): string | null {
  if (!candidate) {
    return null
  }

  const value = candidate.trim().replace(/\/+$/, '')
  if (!value || isPlaceholderApiUrl(value)) {
    return null
  }

  if (value === '/api') {
    return value
  }

  try {
    const url = new URL(value)
    if (isLocalHost(url.hostname) && !isLocalHost(currentHostname)) {
      return null
    }
    return url.toString().replace(/\/+$/, '')
  } catch {
    return null
  }
}

export function normalizeConfiguredApiUrl(candidate?: string | null) {
  return normalizeConfiguredApiUrlForHostname(candidate, window.location.hostname)
}

export function resolveApiUrl() {
  if (import.meta.env.DEV) {
    return '/api'
  }

  const runtimeApiUrl = normalizeConfiguredApiUrl(window.__APP_CONFIG__?.apiUrl)
  if (runtimeApiUrl) {
    return runtimeApiUrl
  }

  const buildTimeApiUrl = normalizeConfiguredApiUrl(import.meta.env.VITE_API_URL)
  if (buildTimeApiUrl) {
    return buildTimeApiUrl
  }

  if (isLocalHost(window.location.hostname)) {
    return 'http://localhost:8080/api'
  }

  return '/api'
}

export function canonicalRedirectUrlForHref(
  currentHref: string,
  configuredSiteUrl?: string | null,
  isDev = import.meta.env.DEV,
) {
  if (isDev || !configuredSiteUrl) return null

  try {
    const current = new URL(currentHref)
    const canonical = new URL(configuredSiteUrl)
    if (isLocalHost(current.hostname) || current.origin === canonical.origin) {
      return null
    }
    canonical.pathname = current.pathname
    canonical.search = current.search
    canonical.hash = current.hash
    return canonical.toString()
  } catch {
    return null
  }
}

export function enforceCanonicalSiteUrl() {
  const target = canonicalRedirectUrlForHref(window.location.href, window.__APP_CONFIG__?.siteUrl)
  if (!target) return false
  window.location.replace(target)
  return true
}
