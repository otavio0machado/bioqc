import {
  AlertTriangle,
  Archive,
  ArrowUpRight,
  CheckCircle2,
  Clock,
  ClipboardList,
  Inbox,
  Package,
  Search,
  X,
} from 'lucide-react'
import type { ReactNode } from 'react'
import { cn } from '../../../utils/cn'
import type { DashFilter, ReagentStats } from './utils'

interface ReagentsDashboardProps {
  stats: ReagentStats
  dashFilter: DashFilter | null
  onToggleFilter: (filter: DashFilter | null) => void
}

/**
 * Dashboard pos refator v3.
 *
 * Cinco cards principais (status canonicos): Total, Em estoque, Em uso,
 * Vencidos, Inativos. Drop {@code Fora de estoque}.
 *
 * Cards de acao quando ha alertas: Vencem em 7d, Vencem em 30d,
 * Rastreabilidade incompleta, Sem validade, Revisar estoque (V14).
 */
export function ReagentsDashboard({
  stats,
  dashFilter,
  onToggleFilter,
}: ReagentsDashboardProps) {
  const showActionRow =
    stats.expiring7d > 0 ||
    stats.expiring30d > 0 ||
    stats.noTraceability > 0 ||
    stats.noValidity > 0 ||
    stats.needsReview > 0

  return (
    <>
      {showActionRow ? (
        <div className="rounded-3xl border border-amber-200 bg-amber-50/60 p-4">
          <div className="mb-3 flex items-center gap-2">
            <AlertTriangle className="h-4 w-4 text-amber-700" />
            <h4 className="text-base font-semibold text-amber-900">Ação hoje</h4>
            <p className="text-sm text-amber-800/80">Filas que precisam de intervenção imediata.</p>
          </div>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
            <ActionQueueCard
              label="Vencem em 7 dias"
              value={stats.expiring7d}
              description="Planeje substituição ou descarte."
              icon={<AlertTriangle className="h-4 w-4" />}
              tone="red"
              active={dashFilter === 'expiring7d'}
              onClick={() => onToggleFilter(dashFilter === 'expiring7d' ? null : 'expiring7d')}
            />
            <ActionQueueCard
              label="Vencem em 30 dias"
              value={stats.expiring30d}
              description="Programe rotação de estoque."
              icon={<Clock className="h-4 w-4" />}
              tone="amber"
              active={dashFilter === 'expiring30d'}
              onClick={() => onToggleFilter(dashFilter === 'expiring30d' ? null : 'expiring30d')}
            />
            <ActionQueueCard
              label="Rastreabilidade incompleta"
              value={stats.noTraceability}
              description="Campos operacionais essenciais ainda pendentes."
              icon={<ClipboardList className="h-4 w-4" />}
              tone="amber"
              active={dashFilter === 'noTraceability'}
              onClick={() => onToggleFilter(dashFilter === 'noTraceability' ? null : 'noTraceability')}
            />
            <ActionQueueCard
              label="Sem validade"
              value={stats.noValidity}
              description="Lotes sem data de validade preenchida."
              icon={<Clock className="h-4 w-4" />}
              tone="amber"
              active={dashFilter === 'noValidity'}
              onClick={() => onToggleFilter(dashFilter === 'noValidity' ? null : 'noValidity')}
            />
            <ActionQueueCard
              label="Revisar estoque"
              value={stats.needsReview}
              description="Lotes da migração V14 com estoque a confirmar."
              icon={<Search className="h-4 w-4" />}
              tone="amber"
              active={dashFilter === 'needsReview'}
              onClick={() => onToggleFilter(dashFilter === 'needsReview' ? null : 'needsReview')}
            />
          </div>
        </div>
      ) : null}

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
        <DashCard
          label="Total"
          value={stats.total}
          icon={<Package className="h-5 w-5" />}
          color="blue"
          active={dashFilter === null}
          onClick={() => onToggleFilter(null)}
        />
        <DashCard
          label="Em estoque"
          value={stats.emEstoque}
          icon={<Inbox className="h-5 w-5" />}
          color="green"
          active={dashFilter === 'emEstoque'}
          onClick={() => onToggleFilter(dashFilter === 'emEstoque' ? null : 'emEstoque')}
        />
        <DashCard
          label="Em uso"
          value={stats.emUso}
          icon={<CheckCircle2 className="h-5 w-5" />}
          color="indigo"
          active={dashFilter === 'emUso'}
          onClick={() => onToggleFilter(dashFilter === 'emUso' ? null : 'emUso')}
        />
        <DashCard
          label="Vencidos"
          value={stats.vencidos}
          icon={<X className="h-5 w-5" />}
          color="red"
          active={dashFilter === 'vencidos'}
          onClick={() => onToggleFilter(dashFilter === 'vencidos' ? null : 'vencidos')}
        />
        <DashCard
          label="Inativos"
          value={stats.inativos}
          icon={<Archive className="h-5 w-5" />}
          color="neutral"
          active={dashFilter === 'inativos'}
          onClick={() => onToggleFilter(dashFilter === 'inativos' ? null : 'inativos')}
        />
      </div>
    </>
  )
}

function ActionQueueCard({
  label,
  value,
  description,
  icon,
  tone,
  active,
  onClick,
}: {
  label: string
  value: number
  description: string
  icon: ReactNode
  tone: 'red' | 'amber'
  active: boolean
  onClick: () => void
}) {
  const iconBg = tone === 'red' ? 'bg-red-100 text-red-700' : 'bg-amber-100 text-amber-700'
  const border = active
    ? tone === 'red'
      ? 'border-red-500 bg-red-50 shadow-sm'
      : 'border-amber-500 bg-amber-50 shadow-sm'
    : 'border-neutral-200 bg-white hover:border-neutral-300'

  return (
    <button
      type="button"
      onClick={onClick}
      className={cn('flex items-start gap-3 rounded-2xl border p-4 text-left transition', border)}
    >
      <div className={cn('rounded-lg p-2', iconBg)}>{icon}</div>
      <div className="flex-1">
        <div className="flex items-baseline gap-2">
          <span className="text-2xl font-bold text-neutral-900">{value}</span>
          <span className="text-sm font-medium text-neutral-700">{label}</span>
        </div>
        <p className="mt-1 text-xs text-neutral-500">{description}</p>
        <span
          className={cn(
            'mt-2 inline-flex items-center gap-1 text-xs font-medium',
            tone === 'red' ? 'text-red-700' : 'text-amber-700',
          )}
        >
          Ver lotes <ArrowUpRight className="h-3 w-3" />
        </span>
      </div>
    </button>
  )
}

function DashCard({
  label,
  value,
  icon,
  color,
  active,
  onClick,
}: {
  label: string
  value: number
  icon: ReactNode
  color: string
  active: boolean
  onClick: () => void
}) {
  const colors: Record<string, string> = {
    blue: active ? 'border-blue-500 bg-blue-50 shadow-md' : 'border-neutral-200 hover:border-blue-300',
    green: active ? 'border-green-500 bg-green-50 shadow-md' : 'border-neutral-200 hover:border-green-300',
    indigo: active
      ? 'border-indigo-500 bg-indigo-50 shadow-md'
      : 'border-neutral-200 hover:border-indigo-300',
    red: active ? 'border-red-500 bg-red-50 shadow-md' : 'border-neutral-200 hover:border-red-300',
    amber: active ? 'border-amber-500 bg-amber-50 shadow-md' : 'border-neutral-200 hover:border-amber-300',
    neutral:
      active ? 'border-neutral-500 bg-neutral-100 shadow-md' : 'border-neutral-200 hover:border-neutral-400',
  }
  const iconColors: Record<string, string> = {
    blue: 'bg-blue-100 text-blue-700',
    green: 'bg-green-100 text-green-700',
    indigo: 'bg-indigo-100 text-indigo-700',
    red: 'bg-red-100 text-red-700',
    amber: 'bg-amber-100 text-amber-700',
    neutral: 'bg-neutral-200 text-neutral-600',
  }

  return (
    <button
      type="button"
      onClick={onClick}
      className={cn('flex items-center gap-3 rounded-xl border p-4 text-left transition', colors[color])}
    >
      <div className={cn('rounded-lg p-2', iconColors[color])}>{icon}</div>
      <div>
        <div className="text-2xl font-bold text-neutral-900">{value}</div>
        <div className="text-sm text-neutral-500">{label}</div>
      </div>
    </button>
  )
}
