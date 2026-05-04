import {
  AlertTriangle,
  ArrowRight,
  Beaker,
  CalendarClock,
  Crosshair,
  FileCheck2,
  FileText,
  FlaskConical,
  FlaskRound,
  HeartPulse,
  LayoutDashboard,
  ShieldCheck,
  Wrench,
  type LucideIcon,
} from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import type { ReportCategory, ReportDefinition } from '../../types/reportsV2'
import { Card, EmptyState } from '../ui'

interface ReportCatalogGridProps {
  definitions: ReportDefinition[]
}

const CATEGORY_LABEL: Record<string, string> = {
  CONTROLE_QUALIDADE: 'Controle de Qualidade',
  WESTGARD: 'Análise Westgard',
  REAGENTES: 'Reagentes',
  MANUTENCAO: 'Manutenção',
  CALIBRACAO: 'Calibração',
  CONSOLIDADO: 'Consolidado',
  REGULATORIO: 'Regulatório ANVISA',
  HEMATOLOGIA: 'Hematologia',
}

const CATEGORY_ICON: Record<string, LucideIcon> = {
  CONTROLE_QUALIDADE: FlaskConical,
  WESTGARD: AlertTriangle,
  REAGENTES: FlaskRound,
  MANUTENCAO: Wrench,
  CALIBRACAO: Crosshair,
  CONSOLIDADO: LayoutDashboard,
  REGULATORIO: FileCheck2,
  HEMATOLOGIA: HeartPulse,
}

// Mapeamento do campo `icon` da ReportDefinition para componentes lucide.
const ICON_MAP: Record<string, LucideIcon> = {
  'flask-conical': FlaskConical,
  'alert-triangle': AlertTriangle,
  'beaker': Beaker,
  'wrench': Wrench,
  'crosshair': Crosshair,
  'layout-dashboard': LayoutDashboard,
  'file-check-2': FileCheck2,
}

const CATEGORY_ORDER: ReportCategory[] = [
  'CONTROLE_QUALIDADE',
  'WESTGARD',
  'REAGENTES',
  'MANUTENCAO',
  'CALIBRACAO',
  'CONSOLIDADO',
  'REGULATORIO',
  'HEMATOLOGIA',
]

export function ReportCatalogGrid({ definitions }: ReportCatalogGridProps) {
  const navigate = useNavigate()

  if (definitions.length === 0) {
    return (
      <EmptyState
        icon={<FileText className="h-8 w-8" />}
        title="Nenhum relatorio disponivel"
        description="Seu perfil ainda nao tem nenhum relatorio V2 liberado. Fale com o administrador do laboratorio."
      />
    )
  }

  // Agrupa por categoria preservando a ordem definida. Categorias novas
  // (nao mapeadas) vao ao fim na ordem em que aparecerem.
  const groups = new Map<string, ReportDefinition[]>()
  for (const def of definitions) {
    const category = String(def.category)
    if (!groups.has(category)) groups.set(category, [])
    groups.get(category)!.push(def)
  }
  const orderedCategories: string[] = [
    ...CATEGORY_ORDER.filter((c) => groups.has(c)),
    ...Array.from(groups.keys()).filter((c) => !CATEGORY_ORDER.includes(c as ReportCategory)),
  ]

  return (
    <div className="space-y-8">
      {orderedCategories.map((category) => {
        const items = groups.get(category) ?? []
        const Icon = CATEGORY_ICON[category] ?? FileText
        const label = CATEGORY_LABEL[category] ?? category
        const countLabel = items.length === 1 ? '1 relatório' : `${items.length} relatórios`
        return (
          <section key={category} className="space-y-3">
            <header className="flex items-center gap-3 border-b border-neutral-200/70 pb-2">
              <div className="rounded-lg bg-green-100 p-1.5 text-green-800">
                <Icon className="h-4 w-4" />
              </div>
              <h2 className="text-base font-semibold text-neutral-900">{label}</h2>
              <span className="rounded-full bg-neutral-100 px-2 py-0.5 text-xs font-medium text-neutral-600">
                {countLabel}
              </span>
            </header>
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {items.map((def) => (
                <DefinitionCard
                  key={def.code}
                  definition={def}
                  onOpen={() => navigate(`/relatorios/${def.code}`)}
                />
              ))}
            </div>
          </section>
        )
      })}
    </div>
  )
}

interface DefinitionCardProps {
  definition: ReportDefinition
  onOpen: () => void
}

function DefinitionCard({ definition, onOpen }: DefinitionCardProps) {
  const Icon: LucideIcon =
    (definition.icon ? ICON_MAP[definition.icon] : undefined) ??
    CATEGORY_ICON[String(definition.category)] ??
    FileText
  const summary = definition.subtitle?.trim() || definition.description
  const retentionLabel = formatRetention(definition.retentionDays)
  return (
    <Card
      className="group relative flex h-full flex-col gap-4 p-5 hover:-translate-y-0.5 hover:shadow-elevated"
      onClick={onOpen}
      role="button"
      aria-label={`Abrir relatorio ${definition.name}`}
    >
      <div className="flex items-start gap-3">
        <div className="shrink-0 rounded-xl bg-green-50 p-2.5 text-green-800 ring-1 ring-green-100 transition-colors group-hover:bg-green-100">
          <Icon className="h-5 w-5" />
        </div>
        <div className="min-w-0 flex-1">
          <h3 className="text-base font-semibold leading-tight text-neutral-900">
            {definition.name}
          </h3>
          <p className="mt-1.5 line-clamp-2 text-sm leading-snug text-neutral-600">{summary}</p>
        </div>
      </div>

      <div className="mt-auto flex items-end justify-between gap-3 border-t border-neutral-100 pt-3">
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-neutral-500">
          <span className="inline-flex items-center gap-1 font-medium text-neutral-700">
            {definition.supportedFormats.join(' · ')}
          </span>
          <span className="inline-flex items-center gap-1">
            <CalendarClock className="h-3 w-3" />
            {retentionLabel}
          </span>
          {definition.signatureRequired ? (
            <span className="inline-flex items-center gap-1 font-medium text-purple-700">
              <ShieldCheck className="h-3 w-3" />
              Assinatura
            </span>
          ) : null}
        </div>
        <span className="inline-flex shrink-0 items-center gap-1 text-sm font-semibold text-green-800 transition-transform group-hover:translate-x-0.5">
          Gerar
          <ArrowRight className="h-4 w-4" />
        </span>
      </div>
    </Card>
  )
}

function formatRetention(days: number): string {
  if (!days || days <= 0) return 'Sem retenção'
  if (days < 30) return `${days} dias`
  if (days < 365) {
    const months = Math.round(days / 30)
    return `${months} ${months === 1 ? 'mês' : 'meses'}`
  }
  const years = Math.round((days / 365) * 10) / 10
  const rounded = Number.isInteger(years) ? years.toFixed(0) : years.toFixed(1)
  return `${rounded} ${years === 1 ? 'ano' : 'anos'}`
}
