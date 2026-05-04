import { History, LayoutGrid, Library } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useLocation, useSearchParams } from 'react-router-dom'
import { RelatoriosTab } from '../components/proin/RelatoriosTab'
import { ExecutionsTable } from '../components/relatoriosV2/ExecutionsTable'
import { ReportCatalogGrid } from '../components/relatoriosV2/ReportCatalogGrid'
import { useReportCatalogV2 } from '../hooks/useReportsV2'
import { Skeleton } from '../components/ui'
import { cn } from '../utils/cn'

type PageTab = 'catalog' | 'history' | 'legacy'

/**
 * Pagina principal de Relatorios com bifurcacao V1/V2.
 *
 * - Quando {@code /relatorios/legado}: forca RelatoriosTab V1.
 * - Quando V2 desligada (catalog retorna 404/[] por flag off): V1 puro.
 * - Quando V2 ligada: tab-switcher com Catalogo, Historico, Legado.
 * - Quando V2 ligada mas sem definitions (role sem acesso): mostra
 *   estado vazio no Catalogo + oferece tabs normais.
 */
export function RelatoriosPage() {
  const [searchParams] = useSearchParams()
  const area = searchParams.get('area') ?? 'bioquimica'
  const location = useLocation()
  const forceLegacy = location.pathname.startsWith('/relatorios/legado')

  const catalog = useReportCatalogV2()
  const [activeTab, setActiveTab] = useState<PageTab>('catalog')

  const showV2Shell = useMemo(() => {
    if (forceLegacy) return false
    if (catalog.isLoading) return false
    return catalog.isV2Enabled && catalog.definitions.length > 0
  }, [forceLegacy, catalog.isLoading, catalog.isV2Enabled, catalog.definitions.length])

  if (forceLegacy) {
    return (
      <div className="mx-auto max-w-7xl space-y-6 px-4 py-8 sm:px-6 lg:px-8">
        <header>
          <h1 className="text-3xl font-bold text-neutral-900">Relatorios (Legado)</h1>
          <p className="text-base text-neutral-500">Geracao e historico na versao V1.</p>
        </header>
        <RelatoriosTab area={area} />
      </div>
    )
  }

  if (catalog.isLoading) {
    return (
      <div className="mx-auto max-w-7xl space-y-6 px-4 py-8 sm:px-6 lg:px-8">
        <header>
          <h1 className="text-3xl font-bold text-neutral-900">Relatorios</h1>
          <p className="text-base text-neutral-500">Carregando catalogo...</p>
        </header>
        <Skeleton height="12rem" />
      </div>
    )
  }

  if (!showV2Shell) {
    // V2 off OU ligada-mas-vazia: delega ao V1. Pode-se debater sobre
    // mostrar estado vazio em "ligada-mas-vazia", mas V1 funcional e menos
    // surpreendente que uma tela vazia.
    return (
      <div className="mx-auto max-w-7xl space-y-6 px-4 py-8 sm:px-6 lg:px-8">
        <header>
          <h1 className="text-3xl font-bold text-neutral-900">Relatorios</h1>
          <p className="text-base text-neutral-500">Geracao e historico de relatorios de CQ.</p>
        </header>
        <RelatoriosTab area={area} />
      </div>
    )
  }

  const totalCount = catalog.definitions.length
  return (
    <div className="mx-auto max-w-7xl space-y-6 px-4 py-8 sm:px-6 lg:px-8">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight text-neutral-900">Relatórios</h1>
          <p className="mt-1 text-sm text-neutral-500">
            Gere, assine e baixe relatórios oficiais do laboratório com histórico completo.
          </p>
        </div>
        <span className="rounded-full border border-neutral-200 bg-white px-3 py-1 text-xs font-medium text-neutral-600 shadow-sm">
          {totalCount} {totalCount === 1 ? 'relatório disponível' : 'relatórios disponíveis'}
        </span>
      </header>

      <div className="inline-flex flex-wrap gap-1 rounded-2xl border border-neutral-200 bg-neutral-50 p-1">
        <TabButton active={activeTab === 'catalog'} onClick={() => setActiveTab('catalog')} icon={<LayoutGrid className="h-4 w-4" />}>
          Catalogo V2
        </TabButton>
        <TabButton active={activeTab === 'history'} onClick={() => setActiveTab('history')} icon={<History className="h-4 w-4" />}>
          Historico
        </TabButton>
        <TabButton active={activeTab === 'legacy'} onClick={() => setActiveTab('legacy')} icon={<Library className="h-4 w-4" />}>
          Legado
        </TabButton>
      </div>

      {activeTab === 'catalog' ? <ReportCatalogGrid definitions={catalog.definitions} /> : null}
      {activeTab === 'history' ? <ExecutionsTable definitions={catalog.definitions} /> : null}
      {activeTab === 'legacy' ? <RelatoriosTab area={area} /> : null}
    </div>
  )
}

interface TabButtonProps {
  active: boolean
  onClick: () => void
  icon: React.ReactNode
  children: React.ReactNode
}

function TabButton({ active, onClick, icon, children }: TabButtonProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'inline-flex items-center gap-2 rounded-xl px-4 py-2 text-sm font-medium transition',
        active
          ? 'bg-white text-green-900 shadow-sm ring-1 ring-neutral-200'
          : 'text-neutral-600 hover:bg-white/60 hover:text-neutral-900',
      )}
      aria-pressed={active}
    >
      {icon}
      {children}
    </button>
  )
}
