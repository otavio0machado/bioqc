import { lazy, Suspense, useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Card, Skeleton } from '../components/ui'
import { useAuth } from '../hooks/useAuth'
import { canImport, canWriteQc } from '../lib/permissions'
import { cn } from '../utils/cn'

const DashboardTab = lazy(() => import('../components/proin/DashboardTab').then((module) => ({ default: module.DashboardTab })))
const HematologiaArea = lazy(() =>
  import('../components/proin/HematologiaArea').then((module) => ({ default: module.HematologiaArea })),
)
const ImportarTab = lazy(() => import('../components/proin/ImportarTab').then((module) => ({ default: module.ImportarTab })))
const ImunologiaArea = lazy(() =>
  import('../components/proin/ImunologiaArea').then((module) => ({ default: module.ImunologiaArea })),
)
const MicrobiologiaArea = lazy(() =>
  import('../components/proin/MicrobiologiaArea').then((module) => ({ default: module.MicrobiologiaArea })),
)
const ParasitologiaArea = lazy(() =>
  import('../components/proin/ParasitologiaArea').then((module) => ({ default: module.ParasitologiaArea })),
)
const ReferenciasTab = lazy(() =>
  import('../components/proin/ReferenciasTab').then((module) => ({ default: module.ReferenciasTab })),
)
const RegistroTab = lazy(() => import('../components/proin/RegistroTab').then((module) => ({ default: module.RegistroTab })))
const UroanaliseArea = lazy(() =>
  import('../components/proin/UroanaliseArea').then((module) => ({ default: module.UroanaliseArea })),
)

const areas = [
  { value: 'bioquimica', label: 'Bioquímica' },
  { value: 'hematologia', label: 'Hematologia' },
  { value: 'imunologia', label: 'Imunologia' },
  { value: 'parasitologia', label: 'Parasitologia' },
  { value: 'microbiologia', label: 'Microbiologia' },
  { value: 'uroanalise', label: 'Uroanálise' },
]

const allTabs = [
  { value: 'dashboard', label: 'Dashboard CQ' },
  { value: 'registro', label: 'Registro CQ' },
  { value: 'referencias', label: 'Referências' },
  { value: 'importar', label: 'Importar' },
]

export function ProinPage() {
  const { user } = useAuth()
  const tabs = useMemo(() => {
    return allTabs.filter((tab) => {
      if (tab.value === 'registro') return canWriteQc(user)
      if (tab.value === 'importar') return canImport(user)
      return true
    })
  }, [user])
  const [searchParams, setSearchParams] = useSearchParams()
  const currentArea = searchParams.get('area') ?? 'bioquimica'
  const currentTab = currentArea === 'bioquimica' ? (searchParams.get('tab') ?? 'dashboard') : 'registro'

  const handleTabChange = (tab: string) => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current)
      next.set('tab', tab)
      return next
    })
  }

  const renderSpecializedArea = () => {
    switch (currentArea) {
      case 'hematologia':
        return <HematologiaArea />
      case 'imunologia':
        return <ImunologiaArea />
      case 'parasitologia':
        return <ParasitologiaArea />
      case 'microbiologia':
        return <MicrobiologiaArea />
      case 'uroanalise':
        return <UroanaliseArea />
      default:
        return <RegistroTab key={`registro-${currentArea}`} area={currentArea} />
    }
  }

  const renderBioquimicaTab = () => {
    switch (currentTab) {
      case 'dashboard':
        return <DashboardTab area={currentArea} />
      case 'registro':
        return <RegistroTab key={`registro-${currentArea}`} area={currentArea} />
      case 'referencias':
        return <ReferenciasTab area={currentArea} />
      case 'importar':
        return <ImportarTab area={currentArea} />
      default:
        return <DashboardTab area={currentArea} />
    }
  }

  const currentAreaLabel = areas.find((item) => item.value === currentArea)?.label ?? currentArea

  return (
    <div className="mx-auto max-w-7xl space-y-6 px-4 py-8 sm:px-6 lg:px-8">
      <header className="space-y-4">
        <div>
          <h1 className="text-3xl font-bold text-neutral-900">{currentAreaLabel}</h1>
          <p className="text-base text-neutral-500">Operação de CQ da área selecionada — lançamento, rastreabilidade e análise.</p>
        </div>

        {currentArea === 'bioquimica' ? (
          <nav className="flex flex-wrap gap-6 border-b border-neutral-200">
            {tabs.map((tab) => (
              <button
                key={tab.value}
                type="button"
                className={cn(
                  'border-b-2 pb-3 text-base font-medium transition',
                  currentTab === tab.value
                    ? 'border-green-800 text-green-800'
                    : 'border-transparent text-neutral-500 hover:text-neutral-700',
                )}
                onClick={() => handleTabChange(tab.value)}
              >
                {tab.label}
              </button>
            ))}
          </nav>
        ) : null}
      </header>

      {currentArea !== 'bioquimica' ? (
        <div className="space-y-6">
          <Card className="bg-gradient-to-r from-white to-green-50">
            <h2 className="text-xl font-semibold text-neutral-900">
              Área: {areas.find((item) => item.value === currentArea)?.label}
            </h2>
            <p className="mt-2 max-w-3xl text-base text-neutral-600">
              O fluxo abaixo traz o módulo especializado desta área, com parâmetros próprios, histórico operacional
              e exportação de relatório.
            </p>
          </Card>
          <Suspense fallback={<ProinContentFallback />}>{renderSpecializedArea()}</Suspense>
        </div>
      ) : null}

      {currentArea === 'bioquimica' ? (
        <Suspense fallback={<ProinContentFallback />}>{renderBioquimicaTab()}</Suspense>
      ) : null}
    </div>
  )
}

function ProinContentFallback() {
  return (
    <Card>
      <Skeleton height="18rem" />
    </Card>
  )
}
