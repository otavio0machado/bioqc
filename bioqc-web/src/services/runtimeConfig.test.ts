import { afterEach, describe, expect, it } from 'vitest'
import {
  canonicalRedirectUrlForHref,
  normalizeConfiguredApiUrl,
  normalizeConfiguredApiUrlForHostname,
  resolveApiUrl,
} from './runtimeConfig'

afterEach(() => {
  window.__APP_CONFIG__ = undefined
})

describe('runtimeConfig', () => {
  it('ignora placeholder de deploy', () => {
    expect(normalizeConfiguredApiUrl('https://seu-backend.up.railway.app/api')).toBeNull()
  })

  it('ignora localhost em producao remota', () => {
    expect(normalizeConfiguredApiUrlForHostname('http://localhost:8080/api', 'labbio.app')).toBeNull()
  })

  it('mantem URL absoluta valida sem barra final', () => {
    expect(normalizeConfiguredApiUrl('https://api.labbio.app/api/')).toBe('https://api.labbio.app/api')
  })

  it('prefere runtime config sobre build-time', () => {
    window.__APP_CONFIG__ = {
      apiUrl: 'https://api.labbio.app/api',
    }

    if (import.meta.env.DEV) {
      expect(resolveApiUrl()).toBe('/api')
      return
    }

    expect(resolveApiUrl()).toBe('https://api.labbio.app/api')
  })

  it('monta redirect canonico preservando path, busca e hash', () => {
    expect(
      canonicalRedirectUrlForHref(
        'https://labbio.app/relatorios?tab=history#top',
        'https://www.labbio.app',
        false,
      ),
    ).toBe('https://www.labbio.app/relatorios?tab=history#top')
  })

  it('nao redireciona quando ja esta no host canonico', () => {
    expect(
      canonicalRedirectUrlForHref(
        'https://www.labbio.app/relatorios',
        'https://www.labbio.app',
        false,
      ),
    ).toBeNull()
  })
})
