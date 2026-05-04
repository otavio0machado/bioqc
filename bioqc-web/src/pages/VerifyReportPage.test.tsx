import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { VerifyReportResponse } from '../types/reportsV2'
import { VerifyReportPage } from './VerifyReportPage'

const mockVerify = vi.fn()

vi.mock('../services/reportsV2Service', async () => {
  const actual = await vi.importActual<typeof import('../services/reportsV2Service')>(
    '../services/reportsV2Service',
  )
  return {
    ...actual,
    reportsV2Service: {
      ...actual.reportsV2Service,
      verify: (hash: string) => mockVerify(hash),
    },
  }
})

function renderAt(hash: string, overrides?: { retry?: boolean | number }) {
  const queryClient = new QueryClient({
    // Forca retry=false no teste. O hook chama {@code retry: 1} em runtime
    // pensando em falhas transientes reais; em teste nao queremos esperar
    // o backoff do react-query para ver o estado de erro.
    defaultOptions: { queries: { retry: overrides?.retry ?? false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/r/verify/${hash}`]}>
        <Routes>
          <Route path="/r/verify/:hash" element={<VerifyReportPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  mockVerify.mockReset()
})

const baseValid: VerifyReportResponse = {
  reportNumber: 'BIO-202604-000001',
  reportCode: 'CQ_OPERATIONAL_V2',
  periodLabel: 'Abril/2026',
  generatedAt: '2026-04-20T10:00:00Z',
  generatedByName: 'ana',
  sha256: '8'.repeat(64),
  signatureHash: null,
  signedAt: null,
  signedByName: null,
  signed: false,
  valid: true,
}

describe('VerifyReportPage', () => {
  it('mostra banner verde quando valid=true e signed=true', async () => {
    mockVerify.mockResolvedValue({
      ...baseValid,
      signed: true,
      signatureHash: '9'.repeat(64),
      signedAt: '2026-04-20T11:00:00Z',
      signedByName: 'Dra. Responsavel',
    } satisfies VerifyReportResponse)

    renderAt('abc')

    expect(await screen.findByText(/Documento valido e assinado/)).toBeInTheDocument()
    expect(screen.getByText('BIO-202604-000001')).toBeInTheDocument()
    expect(screen.getByText(/Dra. Responsavel/)).toBeInTheDocument()
  })

  it('mostra banner azul quando valid=true e signed=false', async () => {
    mockVerify.mockResolvedValue(baseValid)

    renderAt('abc')

    expect(await screen.findByText(/Documento valido \(n.o assinado\)/)).toBeInTheDocument()
  })

  it('mostra banner vermelho quando valid=false', async () => {
    mockVerify.mockResolvedValue({
      reportNumber: null,
      reportCode: null,
      periodLabel: null,
      generatedAt: null,
      generatedByName: null,
      sha256: null,
      signatureHash: null,
      signedAt: null,
      signedByName: null,
      signed: false,
      valid: false,
    } satisfies VerifyReportResponse)

    renderAt('deadbeef')

    expect(await screen.findByText(/Hash desconhecido/)).toBeInTheDocument()
    expect(screen.getByText('deadbeef')).toBeInTheDocument()
  })

  it('mostra estado de loading antes de resolver', async () => {
    let resolveFn: (value: VerifyReportResponse) => void = () => {}
    mockVerify.mockImplementation(
      () =>
        new Promise<VerifyReportResponse>((resolve) => {
          resolveFn = resolve
        }),
    )

    renderAt('abc')

    // Banner ainda nao renderizou
    expect(screen.queryByText(/Documento valido/)).not.toBeInTheDocument()
    expect(screen.queryByText(/Hash desconhecido/)).not.toBeInTheDocument()

    resolveFn(baseValid)
    await waitFor(() => {
      expect(screen.getByText(/Documento valido/)).toBeInTheDocument()
    })
  })

  it('mostra banner neutro em caso de erro de rede', async () => {
    mockVerify.mockRejectedValue(new Error('Network down'))

    // useVerifyReport tem retry:1 interno - aguarda o backoff do react-query
    // antes de exibir o estado de erro. waitFor default de 1s pode falhar,
    // subimos o timeout explicitamente.
    renderAt('abc')

    await waitFor(
      () => {
        expect(
          screen.getByText(/N.o foi poss.vel consultar o servi.o de verifica..o/),
        ).toBeInTheDocument()
      },
      { timeout: 5000 },
    )
  })
})
