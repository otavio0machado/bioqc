import {
  AlertTriangle,
  Archive,
  ArrowDownLeft,
  ArrowUpRight,
  CalendarClock,
  ChevronDown,
  ChevronUp,
  ClipboardList,
  Clock,
  Lock,
  MapPin,
  Minus,
  Package,
  PackagePlus,
  Pencil,
  Plus,
  RotateCcw,
  ShieldCheck,
  Thermometer,
  Trash2,
  Truck,
  Unlock,
} from 'lucide-react'
import type { ReactNode } from 'react'
import type { ReagentLabelSummary, ReagentLot, StockMovement } from '../../../types'
import { cn } from '../../../utils/cn'
import { formatLongBR } from '../../../utils/date'
import { Button, Card, EmptyState, Skeleton, StatusBadge } from '../../ui'
import { MOVEMENT_REASONS, REAGENT_STATUS_LABELS, TAG_STATUS_TABS } from './constants'
import {
  canCloseUnit,
  canOpenUnit,
  canReceiveEntry,
  getLotVisualState,
  getTotalUnits,
  getTraceabilityIssueLabels,
  getTraceabilityIssues,
  type ReagentViewMode,
} from './utils'

interface ReagentsContentProps {
  viewMode: ReagentViewMode
  isLoading?: boolean
  isError?: boolean
  searchTerm: string
  labels: ReagentLabelSummary[]
  lots: ReagentLot[]
  filteredLots: ReagentLot[]
  expandedTag: string | null
  tagStatusTab: string
  expandedLot: ReagentLot | null
  movements: StockMovement[]
  /** Usuario admin? Habilita botao Apagar (refator v3, decisao 1.11). */
  canHardDelete?: boolean
  onExpandedTagChange: (tag: string | null) => void
  onTagStatusTabChange: (status: string) => void
  onExpandedLotChange: (lot: ReagentLot | null) => void
  onOpenEntry: (lot: ReagentLot) => void
  onOpenExit: (lot: ReagentLot) => void
  onOpenAjuste: (lot: ReagentLot) => void
  onOpenEdit: (lot: ReagentLot) => void
  /** ABERTURA q=1: -1 estoque +1 uso. */
  onOpenUnit: (lot: ReagentLot) => void
  /** FECHAMENTO q=1: -1 uso +1 estoque (reverter abertura por engano). */
  onCloseUnit: (lot: ReagentLot) => void
  /** Abre ArchiveLotModal. */
  onArchiveLot: (lot: ReagentLot) => void
  /** Abre DeleteLotModal (admin only). */
  onDeleteLot: (lot: ReagentLot) => void
  /** Reativa lote inativo. */
  onUnarchiveLot: (lot: ReagentLot) => void
  onOpenCreate: () => void
  onRetry?: () => void
}

/**
 * Conteudo principal da aba (lista ou visao por etiquetas).
 *
 * Pos refator v2:
 * - Lista usa {@code lot.label} para busca/header.
 * - Card dentro de etiqueta nao mostra nome — mostra "Lote {n} . {fabricante}".
 * - Sem barra de progresso de estoque (perdeu sentido sem {@code quantityValue}).
 * - Botoes operacionais separados para ENTRADA e SAIDA.
 */
export function ReagentsContent({
  viewMode,
  isLoading = false,
  isError = false,
  searchTerm,
  labels,
  lots,
  filteredLots,
  expandedTag,
  tagStatusTab,
  expandedLot,
  movements,
  canHardDelete = false,
  onExpandedTagChange,
  onTagStatusTabChange,
  onExpandedLotChange,
  onOpenEntry,
  onOpenExit,
  onOpenAjuste,
  onOpenEdit,
  onOpenUnit,
  onCloseUnit,
  onArchiveLot,
  onDeleteLot,
  onUnarchiveLot,
  onOpenCreate,
  onRetry,
}: ReagentsContentProps) {
  if (isLoading) {
    return <ReagentListSkeleton />
  }

  if (isError) {
    return (
      <EmptyState
        icon={<AlertTriangle className="h-8 w-8" />}
        title="Não foi possível carregar reagentes"
        description="Tente novamente antes de registrar novas movimentações."
        action={onRetry ? { label: 'Tentar novamente', onClick: onRetry } : undefined}
      />
    )
  }

  if (viewMode === 'tags') {
    return (
      <ReagentLabelsView
        searchTerm={searchTerm}
        labels={labels}
        lots={lots}
        expandedTag={expandedTag}
        tagStatusTab={tagStatusTab}
        expandedLot={expandedLot}
        movements={movements}
        canHardDelete={canHardDelete}
        onExpandedTagChange={onExpandedTagChange}
        onTagStatusTabChange={onTagStatusTabChange}
        onExpandedLotChange={onExpandedLotChange}
        onOpenEntry={onOpenEntry}
        onOpenExit={onOpenExit}
        onOpenAjuste={onOpenAjuste}
        onOpenEdit={onOpenEdit}
        onOpenUnit={onOpenUnit}
        onCloseUnit={onCloseUnit}
        onArchiveLot={onArchiveLot}
        onDeleteLot={onDeleteLot}
        onUnarchiveLot={onUnarchiveLot}
      />
    )
  }

  if (filteredLots.length === 0) {
    return (
      <EmptyState
        icon={<PackagePlus className="h-8 w-8" />}
        title="Nenhum lote encontrado"
        description="Cadastre um lote ou limpe os filtros."
        action={{ label: 'Novo Lote', onClick: onOpenCreate }}
      />
    )
  }

  return (
    <div className="space-y-3">
      {filteredLots.map((lot) => (
        <ReagentListCard
          key={lot.id}
          lot={lot}
          isExpanded={expandedLot?.id === lot.id}
          movements={movements}
          canHardDelete={canHardDelete}
          onToggleHistory={() => onExpandedLotChange(expandedLot?.id === lot.id ? null : lot)}
          onOpenEntry={() => onOpenEntry(lot)}
          onOpenExit={() => onOpenExit(lot)}
          onOpenAjuste={() => onOpenAjuste(lot)}
          onOpenEdit={() => onOpenEdit(lot)}
          onOpenUnit={() => onOpenUnit(lot)}
          onCloseUnit={() => onCloseUnit(lot)}
          onArchiveLot={() => onArchiveLot(lot)}
          onDeleteLot={() => onDeleteLot(lot)}
          onUnarchiveLot={() => onUnarchiveLot(lot)}
        />
      ))}
    </div>
  )
}

function ReagentListSkeleton() {
  return (
    <div className="space-y-3" aria-label="Carregando lotes de reagentes">
      {Array.from({ length: 3 }).map((_, index) => (
        <Card key={index} className="space-y-4">
          <div className="flex items-center justify-between gap-4">
            <div className="space-y-2">
              <Skeleton width="14rem" height="1.25rem" />
              <Skeleton width="22rem" height="0.875rem" />
            </div>
            <Skeleton width="7rem" height="2rem" />
          </div>
          <div className="grid gap-2 sm:grid-cols-4">
            <Skeleton height="3rem" />
            <Skeleton height="3rem" />
            <Skeleton height="3rem" />
            <Skeleton height="3rem" />
          </div>
        </Card>
      ))}
    </div>
  )
}

function ReagentLabelsView({
  searchTerm,
  labels,
  lots,
  expandedTag,
  tagStatusTab,
  expandedLot,
  movements,
  canHardDelete,
  onExpandedTagChange,
  onTagStatusTabChange,
  onExpandedLotChange,
  onOpenEntry,
  onOpenExit,
  onOpenAjuste,
  onOpenEdit,
  onOpenUnit,
  onCloseUnit,
  onArchiveLot,
  onDeleteLot,
  onUnarchiveLot,
}: Omit<ReagentsContentProps, 'viewMode' | 'filteredLots' | 'onOpenCreate'>) {
  const filteredLabels = labels.filter(
    (label) => !searchTerm || label.label.toLowerCase().includes(searchTerm.toLowerCase()),
  )

  if (expandedTag === null) {
    if (filteredLabels.length === 0) {
      return (
        <EmptyState
          icon={<Package className="h-8 w-8" />}
          title="Nenhuma etiqueta encontrada"
          description="Cadastre lotes com etiquetas para começar a agrupá-los."
        />
      )
    }

    return (
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {filteredLabels.map((summary) => (
          <button
            key={summary.label}
            type="button"
            onClick={() => {
              onExpandedTagChange(summary.label)
              onTagStatusTabChange('todos')
            }}
            className="rounded-2xl border border-neutral-200 bg-white p-4 text-left transition-all hover:border-green-300 hover:shadow-md"
          >
            <p className="font-semibold text-lg text-neutral-800">{summary.label}</p>
            <p className="text-sm text-neutral-500">
              {summary.total} lote{summary.total !== 1 ? 's' : ''}
            </p>
            <div className="mt-2 flex flex-wrap gap-1.5">
              {summary.emEstoque > 0 ? (
                <span className="rounded-full bg-green-100 px-2 py-0.5 text-xs text-green-700">
                  {summary.emEstoque} em estoque
                </span>
              ) : null}
              {summary.emUso > 0 ? (
                <span className="rounded-full bg-blue-100 px-2 py-0.5 text-xs text-blue-700">
                  {summary.emUso} em uso
                </span>
              ) : null}
              {summary.inativos > 0 ? (
                <span className="rounded-full bg-neutral-100 px-2 py-0.5 text-xs text-neutral-600">
                  {summary.inativos} inativo{summary.inativos !== 1 ? 's' : ''}
                </span>
              ) : null}
              {summary.vencidos > 0 ? (
                <span className="rounded-full bg-red-100 px-2 py-0.5 text-xs text-red-700">
                  {summary.vencidos} vencido{summary.vencidos !== 1 ? 's' : ''}
                </span>
              ) : null}
            </div>
          </button>
        ))}
      </div>
    )
  }

  // Refator v3.1: "Todos" oculta inativos (lotes arquivados). O usuario ve
  // inativos apenas ao clicar explicitamente na aba "Inativo". Mantem
  // arquivamento como estado terminal sem poluir a visao operacional.
  const tagLots = lots.filter((lot) => {
    if (lot.label !== expandedTag) return false
    if (tagStatusTab === 'todos') return lot.status !== 'inativo'
    return lot.status === tagStatusTab
  })

  return (
    <div>
      <div className="mb-4 flex items-center gap-3">
        <button
          type="button"
          onClick={() => onExpandedTagChange(null)}
          className="text-green-700 hover:text-green-800 text-sm font-medium"
        >
          &larr; Voltar
        </button>
        <h3 className="text-xl font-bold text-neutral-800">{expandedTag}</h3>
      </div>

      <div className="mb-4 flex flex-wrap gap-2">
        {TAG_STATUS_TABS.map((tab) => (
          <button
            key={tab}
            type="button"
            onClick={() => onTagStatusTabChange(tab)}
            className={cn(
              'rounded-full px-3 py-1 text-sm',
              tagStatusTab === tab
                ? 'bg-green-700 text-white'
                : 'bg-neutral-100 text-neutral-600 hover:bg-neutral-200',
            )}
          >
            {tab === 'todos' ? 'Todos' : REAGENT_STATUS_LABELS[tab] ?? tab}
          </button>
        ))}
      </div>

      {tagLots.length === 0 ? (
        <div className="rounded-xl border border-neutral-200 bg-neutral-50 px-4 py-8 text-center text-base text-neutral-500">
          Nenhum lote encontrado para este filtro.
        </div>
      ) : (
        <div className="space-y-3">
          {tagLots.map((lot) => (
            <ReagentTagCard
              key={lot.id}
              lot={lot}
              isExpanded={expandedLot?.id === lot.id}
              movements={movements}
              canHardDelete={canHardDelete ?? false}
              onToggleHistory={() => onExpandedLotChange(expandedLot?.id === lot.id ? null : lot)}
              onOpenEntry={() => onOpenEntry(lot)}
              onOpenExit={() => onOpenExit(lot)}
              onOpenAjuste={() => onOpenAjuste(lot)}
              onOpenEdit={() => onOpenEdit(lot)}
              onOpenUnit={() => onOpenUnit(lot)}
              onCloseUnit={() => onCloseUnit(lot)}
              onArchiveLot={() => onArchiveLot(lot)}
              onDeleteLot={() => onDeleteLot(lot)}
              onUnarchiveLot={() => onUnarchiveLot(lot)}
            />
          ))}
        </div>
      )}
    </div>
  )
}

interface LotCardCallbacks {
  onToggleHistory: () => void
  onOpenEntry: () => void
  onOpenExit: () => void
  onOpenAjuste: () => void
  onOpenEdit: () => void
  onOpenUnit: () => void
  onCloseUnit: () => void
  onArchiveLot: () => void
  onDeleteLot: () => void
  onUnarchiveLot: () => void
}

function ReagentListCard({
  lot,
  isExpanded,
  movements,
  canHardDelete,
  onToggleHistory,
  onOpenEntry,
  onOpenExit,
  onOpenEdit,
  onOpenUnit,
  onCloseUnit,
  onArchiveLot,
  onDeleteLot,
  onUnarchiveLot,
}: {
  lot: ReagentLot
  isExpanded: boolean
  movements: StockMovement[]
  canHardDelete: boolean
} & LotCardCallbacks) {
  const { daysLeft, expired, urgent, warning } = getLotVisualState(lot)
  const traceabilityIssues = getTraceabilityIssues(lot)
  const traceabilityIssueLabels = getTraceabilityIssueLabels(lot)

  return (
    <Card
      className={cn(
        'space-y-3',
        expired && 'border-red-200 bg-red-50/50',
        urgent && !expired && 'border-red-200 bg-red-50/30',
        warning && !expired && !urgent && 'border-amber-200 bg-amber-50/30',
      )}
    >
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <h4 className="text-lg font-semibold text-neutral-900">{lot.label}</h4>
            <StatusBadge status={lot.status} />
            {lot.category ? (
              <span className="rounded-full bg-blue-100 px-2.5 py-1 text-xs font-medium text-blue-800">
                {lot.category}
              </span>
            ) : null}
          </div>
          <div className="mt-1 flex flex-wrap items-center gap-3 text-sm text-neutral-500">
            <span>Lote: {lot.lotNumber}</span>
            {lot.manufacturer ? (
              <span>{lot.manufacturer}</span>
            ) : (
              <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800">
                <AlertTriangle className="h-3 w-3" /> Sem fabricante
              </span>
            )}
            {!lot.expiryDate ? (
              <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800">
                <AlertTriangle className="h-3 w-3" /> Sem validade
              </span>
            ) : null}
            {lot.storageTemp ? (
              <span className="flex items-center gap-1">
                <Thermometer className="h-3 w-3" />
                {lot.storageTemp}
              </span>
            ) : null}
            {lot.location ? (
              <span className="flex items-center gap-1 text-neutral-500">
                <MapPin className="h-3 w-3" />
                {lot.location}
              </span>
            ) : null}
            {lot.supplier ? <span className="text-neutral-500">Fornecedor: {lot.supplier}</span> : null}
            {traceabilityIssues.length > 0 ? (
              <span
                className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800"
                title={`Campos pendentes: ${traceabilityIssueLabels.join(', ')}`}
              >
                <ClipboardList className="h-3 w-3" /> Rastreabilidade incompleta
              </span>
            ) : (
              <span className="inline-flex items-center gap-1 rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-medium text-emerald-800">
                <ShieldCheck className="h-3 w-3" /> Rastreado
              </span>
            )}
            {lot.usedInQcRecently ? (
              <span
                className="inline-flex items-center gap-1 rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-medium text-emerald-800"
                title="Lote apareceu em CQ nos últimos 30 dias"
              >
                Em CQ recente
              </span>
            ) : null}
          </div>
        </div>
        <div className="flex items-center gap-3">
          <span
            className={cn(
              'rounded-full px-3 py-1.5 text-sm font-semibold',
              expired
                ? 'bg-red-600 text-white'
                : urgent
                  ? 'bg-red-100 text-red-800'
                  : warning
                    ? 'bg-amber-100 text-amber-800'
                    : 'bg-green-100 text-green-800',
            )}
          >
            {expired
              ? 'Vencido'
              : urgent
                ? `${daysLeft}d restantes`
                : warning
                  ? `${daysLeft}d`
                  : lot.expiryDate
                    ? formatLongBR(lot.expiryDate)
                    : '—'}
          </span>
        </div>
      </div>

      <StockSummary lot={lot} />

      <LotOperationalDetails lot={lot} />

      <LotActionButtons
        lot={lot}
        canHardDelete={canHardDelete}
        isHistoryExpanded={isExpanded}
        onOpenEntry={onOpenEntry}
        onOpenExit={onOpenExit}
        onOpenEdit={onOpenEdit}
        onOpenUnit={onOpenUnit}
        onCloseUnit={onCloseUnit}
        onArchiveLot={onArchiveLot}
        onDeleteLot={onDeleteLot}
        onUnarchiveLot={onUnarchiveLot}
        onToggleHistory={onToggleHistory}
      />

      {isExpanded ? <MovementHistoryPanel movements={movements} /> : null}
    </Card>
  )
}

function ReagentTagCard({
  lot,
  isExpanded,
  movements,
  canHardDelete,
  onToggleHistory,
  onOpenEntry,
  onOpenExit,
  onOpenEdit,
  onOpenUnit,
  onCloseUnit,
  onArchiveLot,
  onDeleteLot,
  onUnarchiveLot,
}: {
  lot: ReagentLot
  isExpanded: boolean
  movements: StockMovement[]
  canHardDelete: boolean
} & LotCardCallbacks) {
  const { daysLeft, expired, urgent, warning } = getLotVisualState(lot)
  const traceabilityIssues = getTraceabilityIssues(lot)
  const traceabilityIssueLabels = getTraceabilityIssueLabels(lot)

  return (
    <Card
      className={cn(
        'space-y-3',
        expired && 'border-red-200 bg-red-50/50',
        urgent && !expired && 'border-red-200 bg-red-50/30',
        warning && !expired && !urgent && 'border-amber-200 bg-amber-50/30',
      )}
    >
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <h4 className="text-lg font-semibold text-neutral-900">
              Lote {lot.lotNumber} · {lot.manufacturer || 'Sem fabricante'}
            </h4>
            <StatusBadge status={lot.status} />
            {lot.category ? (
              <span className="rounded-full bg-blue-100 px-2.5 py-1 text-xs font-medium text-blue-800">
                {lot.category}
              </span>
            ) : null}
          </div>
          <div className="mt-1 flex flex-wrap items-center gap-3 text-sm text-neutral-500">
            {lot.storageTemp ? (
              <span className="flex items-center gap-1">
                <Thermometer className="h-3 w-3" />
                {lot.storageTemp}
              </span>
            ) : null}
            {lot.location ? (
              <span className="flex items-center gap-1">
                <MapPin className="h-3 w-3" />
                {lot.location}
              </span>
            ) : null}
            {lot.expiryDate ? (
              <span className="flex items-center gap-1">
                <Clock className="h-3 w-3" />
                {formatLongBR(lot.expiryDate)}
              </span>
            ) : (
              <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800">
                <AlertTriangle className="h-3 w-3" /> Sem validade
              </span>
            )}
            {traceabilityIssues.length > 0 ? (
              <span
                className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800"
                title={`Campos pendentes: ${traceabilityIssueLabels.join(', ')}`}
              >
                <ClipboardList className="h-3 w-3" /> Rastreabilidade incompleta
              </span>
            ) : null}
          </div>
        </div>
        <span
          className={cn(
            'rounded-full px-3 py-1.5 text-sm font-semibold',
            expired
              ? 'bg-red-600 text-white'
              : urgent
                ? 'bg-red-100 text-red-800'
                : warning
                  ? 'bg-amber-100 text-amber-800'
                  : 'bg-green-100 text-green-800',
          )}
        >
          {expired
            ? 'Vencido'
            : urgent
              ? `${daysLeft}d restantes`
              : warning
                ? `${daysLeft}d`
                : lot.expiryDate
                  ? formatLongBR(lot.expiryDate)
                  : '—'}
        </span>
      </div>

      <StockSummary lot={lot} />

      <LotOperationalDetails lot={lot} />

      <LotActionButtons
        lot={lot}
        canHardDelete={canHardDelete}
        isHistoryExpanded={isExpanded}
        onOpenEntry={onOpenEntry}
        onOpenExit={onOpenExit}
        onOpenEdit={onOpenEdit}
        onOpenUnit={onOpenUnit}
        onCloseUnit={onCloseUnit}
        onArchiveLot={onArchiveLot}
        onDeleteLot={onDeleteLot}
        onUnarchiveLot={onUnarchiveLot}
        onToggleHistory={onToggleHistory}
      />

      {isExpanded ? <MovementHistoryPanel movements={movements} /> : null}
    </Card>
  )
}

/**
 * Conjunto canonico de botoes operacionais por card pos refator v3.1.
 *
 * Layout:
 * - Operacoes principais (linha 1): Adicionar (ENTRADA), Abrir unidade (ABERTURA),
 *   Voltar ao estoque (FECHAMENTO), Final de Uso (CONSUMO).
 * - Manutencao (linha 2): Editar, Arquivar | Apagar (admin only), Histórico.
 * - Em {@code inativo}: substitui as principais por "Reativar".
 *
 * Refator v3.1:
 * - Drop botao "Ajuste" da UI (callback {@code onOpenAjuste} segue na interface
 *   por compatibilidade — backend ainda aceita AJUSTE, so nao expomos botao).
 * - Renomeio "Consumir" -> "Final de Uso" (value HTTP CONSUMO inalterado).
 */
function LotActionButtons({
  lot,
  canHardDelete,
  isHistoryExpanded,
  onOpenEntry,
  onOpenExit,
  onOpenEdit,
  onOpenUnit,
  onCloseUnit,
  onArchiveLot,
  onDeleteLot,
  onUnarchiveLot,
  onToggleHistory,
}: {
  lot: ReagentLot
  canHardDelete: boolean
  isHistoryExpanded: boolean
} & Omit<LotCardCallbacks, 'onOpenAjuste'>) {
  const isInativo = lot.status === 'inativo'
  const isVencido = lot.status === 'vencido'
  const canEntry = canReceiveEntry(lot)
  const canOpen = canOpenUnit(lot)
  const canClose = canCloseUnit(lot)
  const canConsume = !isInativo && (lot.unitsInUse ?? 0) > 0

  return (
    <div className="space-y-2">
      {isInativo ? (
        <div className="flex flex-wrap items-center gap-2">
          <Button
            variant="secondary"
            size="sm"
            onClick={onUnarchiveLot}
            icon={<RotateCcw className="h-4 w-4" />}
          >
            Reativar lote
          </Button>
        </div>
      ) : (
        <div className="flex flex-wrap items-center gap-2">
          <Button
            variant="secondary"
            size="sm"
            onClick={onOpenEntry}
            disabled={!canEntry}
            icon={<Plus className="h-4 w-4" />}
            title={
              canEntry
                ? 'Registrar entrada de novas unidades em estoque'
                : (lot.movementWarning ??
                  'Lote vencido ou inativo não aceita ENTRADA.')
            }
          >
            Adicionar
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={onOpenUnit}
            disabled={!canOpen}
            icon={<Unlock className="h-4 w-4" />}
            title={
              canOpen
                ? 'Abrir 1 unidade em estoque (mover para Em uso)'
                : 'Sem unidades em estoque para abrir.'
            }
          >
            Abrir unidade
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={onCloseUnit}
            disabled={!canClose}
            icon={<Lock className="h-4 w-4" />}
            title={
              canClose
                ? 'Reverter abertura: voltar 1 unidade ao estoque'
                : 'Sem unidades em uso para voltar ao estoque.'
            }
          >
            Voltar ao estoque
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={onOpenExit}
            disabled={!canConsume && !isVencido}
            icon={<Minus className="h-4 w-4" />}
            title={
              canConsume
                ? 'Registrar fim de uso de uma unidade aberta'
                : 'Sem unidades em uso para registrar fim de uso.'
            }
          >
            Final de Uso
          </Button>
        </div>
      )}

      <div className="flex flex-wrap items-center gap-2">
        <Button
          variant="ghost"
          size="sm"
          onClick={onOpenEdit}
          icon={<Pencil className="h-4 w-4" />}
        >
          Editar
        </Button>
        {!isInativo ? (
          <Button
            variant="ghost"
            size="sm"
            onClick={onArchiveLot}
            icon={<Archive className="h-4 w-4" />}
            title="Arquivar lote (vira Inativo)"
          >
            Arquivar
          </Button>
        ) : null}
        {canHardDelete ? (
          <Button
            variant="ghost"
            size="sm"
            onClick={onDeleteLot}
            icon={<Trash2 className="h-4 w-4 text-red-600" />}
            title="Apagar definitivamente — só ADMIN. Use só para correções de cadastro."
          >
            <span className="text-red-700">Apagar</span>
          </Button>
        ) : null}
        <Button
          variant="ghost"
          size="sm"
          onClick={onToggleHistory}
          icon={
            isHistoryExpanded ? (
              <ChevronUp className="h-4 w-4" />
            ) : (
              <ChevronDown className="h-4 w-4" />
            )
          }
        >
          {isHistoryExpanded ? 'Ocultar' : 'Histórico'}
        </Button>
      </div>
    </div>
  )
}

function StockSummary({ lot }: { lot: ReagentLot }) {
  const inStock = lot.unitsInStock ?? 0
  const inUse = lot.unitsInUse ?? 0
  const total = getTotalUnits(lot)
  return (
    <div className="flex flex-wrap items-center gap-3 rounded-xl bg-neutral-50 px-3 py-2 text-sm text-neutral-600">
      <span className="inline-flex items-center gap-1.5 font-medium text-neutral-800">
        <Package className="h-4 w-4 text-neutral-500" />
        Em estoque: <strong>{inStock}</strong>
      </span>
      <span className="inline-flex items-center gap-1.5 font-medium text-neutral-800">
        <Unlock className="h-4 w-4 text-neutral-500" />
        Em uso: <strong>{inUse}</strong>
      </span>
      <span className="text-neutral-500">Total: {total}</span>
      {lot.openedDate ? (
        <span className="text-xs text-neutral-500">
          Primeira abertura {formatLongBR(lot.openedDate)}
        </span>
      ) : null}
      {lot.status === 'inativo' && lot.archivedAt ? (
        <span className="inline-flex items-center gap-1 rounded-full bg-neutral-200 px-2 py-0.5 text-xs font-medium text-neutral-700">
          Arquivado em {formatLongBR(lot.archivedAt)}
          {lot.archivedBy ? ` por ${lot.archivedBy}` : ''}
        </span>
      ) : null}
      {lot.needsStockReview ? (
        <span
          className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800"
          title="Lote vindo da migração V14 — confirme quantas unidades estão em uso vs. em estoque."
        >
          <AlertTriangle className="h-3 w-3" /> Revisar estoque
        </span>
      ) : null}
    </div>
  )
}

function LotOperationalDetails({ lot }: { lot: ReagentLot }) {
  const expiryLabel = lot.expiryDate ? formatLongBR(lot.expiryDate) : 'Sem validade'
  const receivedLabel = lot.receivedDate ? formatLongBR(lot.receivedDate) : 'Recebimento pendente'
  const openedLabel = lot.openedDate ? formatLongBR(lot.openedDate) : 'Abertura pendente'

  return (
    <div className="grid gap-2 rounded-xl bg-neutral-50 p-3 text-sm sm:grid-cols-2 lg:grid-cols-3">
      <OperationalDetail
        icon={<Clock className="h-4 w-4" />}
        label="Validade"
        value={expiryLabel}
        tone={lot.status === 'vencido' ? 'danger' : lot.nearExpiry ? 'warning' : 'default'}
      />
      <OperationalDetail
        icon={<CalendarClock className="h-4 w-4" />}
        label="Recebimento / abertura"
        value={`${receivedLabel} · ${openedLabel}`}
      />
      <OperationalDetail
        icon={<Truck className="h-4 w-4" />}
        label="Fornecedor"
        value={lot.supplier?.trim() || 'Fornecedor pendente'}
      />
    </div>
  )
}

function OperationalDetail({
  icon,
  label,
  value,
  tone = 'default',
}: {
  icon: ReactNode
  label: string
  value: string
  tone?: 'default' | 'warning' | 'danger'
}) {
  return (
    <div className="min-w-0">
      <div
        className={cn(
          'mb-1 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide',
          tone === 'danger'
            ? 'text-red-700'
            : tone === 'warning'
              ? 'text-amber-700'
              : 'text-neutral-500',
        )}
      >
        {icon}
        {label}
      </div>
      <div className="break-words text-sm font-medium text-neutral-800">{value}</div>
    </div>
  )
}

function MovementHistoryPanel({ movements }: { movements: StockMovement[] }) {
  return (
    <div className="space-y-2 rounded-xl bg-neutral-50 p-3">
      <div className="flex items-center gap-2 text-sm font-medium text-neutral-700">
        <CalendarClock className="h-4 w-4" /> Movimentações
      </div>
      {movements.length ? (
        movements.map((movement) => {
          const reasonLabel =
            MOVEMENT_REASONS.find((reason) => reason.value === movement.reason)?.label ??
            movement.reason
          const isLegacy =
            movement.isLegacy ??
            (typeof movement.previousStock === 'number' &&
              typeof movement.previousUnitsInStock !== 'number')

          // Delta amigavel. Pos-V14: mostra previousUnitsInStock/InUse.
          // Pre-V14: mostra previousStock → nextStock derivado pelo tipo.
          let deltaText: string | null = null
          if (!isLegacy) {
            const ps = movement.previousUnitsInStock ?? null
            const pu = movement.previousUnitsInUse ?? null
            if (ps != null || pu != null) {
              deltaText = `📦${ps ?? '-'} 🔓${pu ?? '-'}`
            }
          } else {
            const ps = movement.previousStock ?? null
            if (ps != null) {
              let next: number | null = null
              if (movement.type === 'ENTRADA') next = ps + movement.quantity
              else if (movement.type === 'SAIDA') next = ps - movement.quantity
              else if (movement.type === 'AJUSTE') next = movement.quantity
              deltaText = next != null ? `${ps} → ${next}` : null
            }
          }

          const presentation = (() => {
            if (movement.type === 'ENTRADA' || movement.type === 'FECHAMENTO') {
              return {
                icon: <ArrowDownLeft className="h-4 w-4 text-green-600" />,
                sign: '+',
                className: 'text-green-700',
              }
            }
            if (
              movement.type === 'SAIDA' ||
              movement.type === 'CONSUMO' ||
              movement.type === 'ABERTURA'
            ) {
              return {
                icon: <ArrowUpRight className="h-4 w-4 text-red-600" />,
                sign: '-',
                className: 'text-red-700',
              }
            }
            return {
              icon: <Pencil className="h-4 w-4 text-blue-600" />,
              sign: '=',
              className: 'text-blue-700',
            }
          })()

          // Refator v3.1: quando movement.eventDate vem preenchido, mostramos
          // a data declarada do evento ao lado do responsavel. Caso contrario,
          // a data principal segue sendo {@code createdAt}.
          const eventDateLabel = movement.eventDate
            ? formatLongBR(movement.eventDate)
            : null

          return (
            <div key={movement.id} className="rounded-lg bg-white px-3 py-2 text-sm">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex flex-wrap items-center gap-2">
                  {presentation.icon}
                  <span className={cn('font-semibold', presentation.className)}>
                    {presentation.sign}
                    {movement.quantity}
                  </span>
                  <span className="rounded-full bg-neutral-100 px-2 py-0.5 text-[10px] uppercase tracking-wide text-neutral-600">
                    {movement.type}
                  </span>
                  {deltaText ? (
                    <span className="font-mono text-xs text-neutral-500">{deltaText}</span>
                  ) : null}
                  {movement.responsible ? (
                    <span className="text-neutral-500">por {movement.responsible}</span>
                  ) : null}
                  {eventDateLabel ? (
                    <>
                      <span className="text-neutral-500">em {eventDateLabel}</span>
                      <span
                        className="rounded-full bg-blue-50 px-2 py-0.5 text-[10px] font-medium text-blue-800"
                        title="Data declarada pelo operador (eventDate)"
                      >
                        data declarada
                      </span>
                    </>
                  ) : null}
                  {reasonLabel ? (
                    <span className="rounded-full bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-800">
                      {reasonLabel}
                    </span>
                  ) : null}
                  {isLegacy ? (
                    <span
                      className="rounded-full bg-neutral-200 px-2 py-0.5 text-[10px] font-medium text-neutral-600"
                      title="Movimento pre-refator v3 (V14)."
                    >
                      Legado
                    </span>
                  ) : null}
                </div>
                <span className="text-xs text-neutral-400">
                  {new Date(movement.createdAt).toLocaleString('pt-BR')}
                </span>
              </div>
              {movement.notes ? (
                <p className="mt-1 break-words text-xs text-neutral-500">{movement.notes}</p>
              ) : null}
            </div>
          )
        })
      ) : (
        <p className="text-sm text-neutral-500">Nenhuma movimentação.</p>
      )}
    </div>
  )
}
