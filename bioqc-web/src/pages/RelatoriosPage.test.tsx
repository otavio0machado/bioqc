import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthContextValue } from '../contexts/auth-context'
import type { ReportDefinition } from '../types/reportsV2'
import { ToastProvider } from '../components/ui'
import { RelatoriosPage } from './RelatoriosPage'

const mockCatalog = vi.fn()

vi.mock('../services/reportsV2Service', async () => {
  const actual = await vi.importActual<typeof import('../services/reportsV2Service')>(
    '../services/reportsV2Service',
  )
  return {
    ...actual,
    reportsV2Service: {
      ...actual.reportsV2Service,
      catalog: () => mockCatalog(),
    },
  }
})

// Mock hooks internos do RelatoriosTab V1 para evitar chamadas reais.
vi.mock('../hooks/useQcRecords', () => ({
  useQcExams: () => ({ data: [] }),
}))

vi.mock('../hooks/useReports', () => ({
  useReportHistory: () => ({ data: [], isLoading: false, refetch: vi.fn() }),
}))

const authValue: AuthContextValue = {
  user: {
    id: 'user-1',
    username: 'admin',
    email: 'admin@bio.com',
    name: 'Admin',
    role: 'ADMIN',
    isActive: true,
    permissions: [],
  },
  isAuthenticated: true,
  isLoading: false,
  login: vi.fn(),
  logout: vi.fn(),
  refreshToken: vi.fn(),
  restoreSession: vi.fn(),
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/relatorios']}>
        <AuthContext.Provider value={authValue}>
          <ToastProvider>
            <RelatoriosPage />
          </ToastProvider>
        </AuthContext.Provider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  mockCatalog.mockReset()
})

const sampleDefinition: ReportDefinition = {
  code: 'CQ_OPERATIONAL_V2',
  name: 'Relatorio Operacional de CQ',
  description: 'CQ com estatisticas e Westgard',
  subtitle: 'CQ completo com Levey-Jennings',
  icon: 'flask-conical',
  category: 'CONTROLE_QUALIDADE',
  supportedFormats: ['PDF'],
  filterSpec: { fields: [] },
  roleAccess: ['ADMIN'],
  signatureRequired: false,
  previewSupported: true,
  aiCommentaryCapable: true,
  retentionDays: 1825,
  legalBasis: 'RDC 786/2023',
}

describe('RelatoriosPage', () => {
  it('renderiza V1 RelatoriosTab quando catalog retorna []', async () => {
    mockCatalog.mockResolvedValue([])

    renderPage()

    await waitFor(() => {
      // V1 tem o titulo "Relatorios" sem tabs V2 - procuramos pelo cabecalho do card V1.
      expect(screen.getByText(/Selecione o per.odo/)).toBeInTheDocument()
    })
    expect(screen.queryByRole('button', { name: /Catalogo V2/i })).not.toBeInTheDocument()
  })

  it('renderiza tabs V2 quando catalog retorna ao menos uma definition', async () => {
    mockCatalog.mockResolvedValue([sampleDefinition])

    renderPage()

    expect(await screen.findByRole('button', { name: /Catalogo V2/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Historico/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Legado/i })).toBeInTheDocument()
    // Aba ativa por default deve ser Catalogo V2, mostrando o card da definition.
    expect(await screen.findByText('Relatorio Operacional de CQ')).toBeInTheDocument()
  })
})
