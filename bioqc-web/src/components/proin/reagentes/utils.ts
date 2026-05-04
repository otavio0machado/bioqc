import type { ComboboxOption } from '../../ui'
import type { ReagentLot, ReagentLotRequest, StockMovementRequest, User } from '../../../types'

export type ReagentSortMode = 'urgency' | 'name' | 'stock'
export type ReagentViewMode = 'list' | 'tags'
/**
 * Filtros operacionais aplicados pelo dashboard. Pos-refator v3:
 * - DROP {@code foraDeEstoque}.
 * - ADD {@code inativos} (substitui o anterior, como sumario do estado terminal manual).
 * - ADD {@code needsReview} (lotes com flag {@code needsStockReview} vindos da V14).
 */
export type DashFilter =
  | 'emEstoque'
  | 'emUso'
  | 'vencidos'
  | 'inativos'
  | 'expiring7d'
  | 'expiring30d'
  | 'noTraceability'
  | 'noValidity'
  | 'needsReview'

/**
 * Stats consumidos pelo {@code ReagentsDashboard}. Cinco contagens principais
 * (status canonicos) + alertas operacionais.
 */
export interface ReagentStats {
  total: number
  emEstoque: number
  emUso: number
  vencidos: number
  inativos: number
  expiring7d: number
  expiring30d: number
  noTraceability: number
  noValidity: number
  needsReview: number
}

export interface ReagentFilters {
  searchTerm: string
  manufacturerFilter: string
  tempFilter: string
  alertsOnly: boolean
  dashFilter: DashFilter | null
  sortMode: ReagentSortMode
}

export function getResponsibleName(user: User | null | undefined) {
  return user?.name ?? user?.username ?? ''
}

/**
 * Form vazio para o {@code ReagentLotModal}. Reflete os 10 obrigatorios canonicos
 * pos-v3 + 3 opcionais (Detalhes adicionais). Status default = 'em_estoque'.
 */
export function createEmptyLotForm(): ReagentLotRequest {
  return {
    label: '',
    lotNumber: '',
    manufacturer: '',
    category: '',
    expiryDate: '',
    unitsInStock: 0,
    unitsInUse: 0,
    status: 'em_estoque',
    location: '',
    storageTemp: '',
    supplier: undefined,
    receivedDate: undefined,
    openedDate: undefined,
  }
}

export function createMovementForm(
  responsible = '',
  type: StockMovementRequest['type'] = 'ENTRADA',
): StockMovementRequest {
  return {
    type,
    quantity: 0,
    responsible,
    notes: '',
    reason: null,
  }
}

/**
 * Lista as chaves ASCII de campos de rastreabilidade pendentes. Quando o backend
 * envia {@code traceabilityIssues} no DTO, prevalece esse valor. Fallback recalcula
 * client-side para defesa em profundidade.
 */
export function getTraceabilityIssues(lot: ReagentLot) {
  if (Array.isArray(lot.traceabilityIssues)) {
    return lot.traceabilityIssues
  }

  const issues: string[] = []

  if (!lot.manufacturer?.trim()) issues.push('manufacturer')
  if (!lot.location?.trim()) issues.push('location')
  if (!lot.supplier?.trim()) issues.push('supplier')
  if (!lot.receivedDate) issues.push('receivedDate')

  return issues
}

export function getTraceabilityIssueLabels(lot: ReagentLot) {
  const labels: Record<string, string> = {
    manufacturer: 'fabricante',
    location: 'localização',
    supplier: 'fornecedor',
    receivedDate: 'recebimento',
  }

  return getTraceabilityIssues(lot).map((issue) => labels[issue] ?? issue)
}

/**
 * Politica canonica de aceitacao de ENTRADA pos-refator v3: {@code vencido} e
 * {@code inativo} bloqueiam. Inativo so aceita AJUSTE (com reason) ou unarchive
 * antes de aceitar movimentos operacionais. Backend grava
 * {@code canReceiveEntry} no DTO; o fallback abaixo replica a regra para o caso
 * raro de o campo nao vir.
 */
export function canReceiveEntry(lot: ReagentLot) {
  if (typeof lot.canReceiveEntry === 'boolean') {
    return lot.canReceiveEntry
  }
  return lot.status !== 'vencido' && lot.status !== 'inativo'
}

/**
 * Pode abrir uma unidade ({@code ABERTURA}) — exige estoque fechado e lote
 * operavel.
 */
export function canOpenUnit(lot: ReagentLot) {
  if (lot.status === 'vencido' || lot.status === 'inativo') return false
  return (lot.unitsInStock ?? 0) >= 1
}

/**
 * Pode reverter uma abertura ({@code FECHAMENTO}) — exige unidade aberta e
 * lote operavel.
 */
export function canCloseUnit(lot: ReagentLot) {
  if (lot.status === 'vencido' || lot.status === 'inativo') return false
  return (lot.unitsInUse ?? 0) >= 1
}

/**
 * Constroi os indicadores do dashboard pos refator v3.
 * Cinco principais sao contagens por status canonico; quatro de alerta operacional.
 */
export function buildReagentStats(lots: ReagentLot[]): ReagentStats {
  const isActive = (lot: ReagentLot) => lot.status !== 'vencido' && lot.status !== 'inativo'
  return {
    total: lots.length,
    emEstoque: lots.filter((lot) => lot.status === 'em_estoque').length,
    emUso: lots.filter((lot) => lot.status === 'em_uso').length,
    vencidos: lots.filter((lot) => lot.status === 'vencido').length,
    inativos: lots.filter((lot) => lot.status === 'inativo').length,
    expiring7d: lots.filter((lot) => isActive(lot) && lot.daysLeft >= 0 && lot.daysLeft <= 7).length,
    expiring30d: lots.filter((lot) => isActive(lot) && lot.daysLeft > 7 && lot.daysLeft <= 30).length,
    noTraceability: lots.filter((lot) => getTraceabilityIssues(lot).length > 0).length,
    noValidity: lots.filter((lot) => !lot.expiryDate).length,
    needsReview: lots.filter((lot) => Boolean(lot.needsStockReview)).length,
  }
}

export function buildManufacturerOptions(lots: ReagentLot[]): ComboboxOption[] {
  return buildLotFieldOptions(lots, (lot) => lot.manufacturer)
}

export function buildLocationOptions(lots: ReagentLot[]): ComboboxOption[] {
  return buildLotFieldOptions(lots, (lot) => lot.location)
}

export function buildSupplierOptions(lots: ReagentLot[]): ComboboxOption[] {
  return buildLotFieldOptions(lots, (lot) => lot.supplier)
}

/**
 * Agrega valores distintos de um campo de lote em opcoes de combobox,
 * ordenadas por frequencia (descendente) e nome (ascendente). A descricao
 * mostra "N lotes" quando ha mais de uma ocorrencia. Usado por
 * Fabricante, Localizacao e Fornecedor para padronizar UX e evitar
 * variantes acidentais de capitalizacao.
 */
function buildLotFieldOptions(
  lots: ReagentLot[],
  getter: (lot: ReagentLot) => string | null | undefined,
): ComboboxOption[] {
  const counts = new Map<string, number>()

  for (const lot of lots) {
    const value = getter(lot)?.trim()
    if (!value) continue
    counts.set(value, (counts.get(value) ?? 0) + 1)
  }

  return Array.from(counts.entries())
    .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
    .map(([value, count]) => ({
      value,
      label: value,
      description: count > 1 ? `${count} lotes` : undefined,
    }))
}

/**
 * Aplica busca, filtros operacionais e ordenacao na lista de lotes.
 * Search consome agora {@code label} (e nao mais {@code name}).
 */
export function filterReagentLots(lots: ReagentLot[], filters: ReagentFilters) {
  let result = lots

  if (filters.searchTerm) {
    const normalizedTerm = filters.searchTerm.toLowerCase()
    result = result.filter(
      (lot) =>
        (lot.label ?? '').toLowerCase().includes(normalizedTerm) ||
        lot.lotNumber.toLowerCase().includes(normalizedTerm),
    )
  }

  if (filters.manufacturerFilter) {
    result = result.filter((lot) => (lot.manufacturer ?? '') === filters.manufacturerFilter)
  }

  if (filters.tempFilter) {
    result = result.filter((lot) => (lot.storageTemp ?? '') === filters.tempFilter)
  }

  if (filters.alertsOnly) {
    result = result.filter(
      (lot) =>
        lot.status === 'vencido' ||
        lot.daysLeft < 0 ||
        (lot.daysLeft >= 0 && lot.daysLeft <= 7) ||
        getTraceabilityIssues(lot).length > 0 ||
        !lot.expiryDate ||
        Boolean(lot.needsStockReview),
    )
  }

  if (filters.dashFilter === 'emEstoque') {
    result = result.filter((lot) => lot.status === 'em_estoque')
  } else if (filters.dashFilter === 'emUso') {
    result = result.filter((lot) => lot.status === 'em_uso')
  } else if (filters.dashFilter === 'vencidos') {
    result = result.filter((lot) => lot.status === 'vencido')
  } else if (filters.dashFilter === 'inativos') {
    result = result.filter((lot) => lot.status === 'inativo')
  } else if (filters.dashFilter === 'expiring7d') {
    result = result.filter(
      (lot) =>
        lot.status !== 'vencido' &&
        lot.status !== 'inativo' &&
        lot.daysLeft >= 0 &&
        lot.daysLeft <= 7,
    )
  } else if (filters.dashFilter === 'expiring30d') {
    result = result.filter(
      (lot) =>
        lot.status !== 'vencido' &&
        lot.status !== 'inativo' &&
        lot.daysLeft > 7 &&
        lot.daysLeft <= 30,
    )
  } else if (filters.dashFilter === 'noTraceability') {
    result = result.filter((lot) => getTraceabilityIssues(lot).length > 0)
  } else if (filters.dashFilter === 'noValidity') {
    result = result.filter((lot) => !lot.expiryDate)
  } else if (filters.dashFilter === 'needsReview') {
    result = result.filter((lot) => Boolean(lot.needsStockReview))
  }

  const sorted = [...result]

  if (filters.sortMode === 'urgency') {
    sorted.sort((a, b) => {
      const aExpired = a.daysLeft < 0 ? 1 : 0
      const bExpired = b.daysLeft < 0 ? 1 : 0
      if (aExpired !== bExpired) return bExpired - aExpired

      const aDays = a.daysLeft ?? Number.MAX_SAFE_INTEGER
      const bDays = b.daysLeft ?? Number.MAX_SAFE_INTEGER
      return aDays - bDays
    })
  } else if (filters.sortMode === 'name') {
    sorted.sort((a, b) => (a.label ?? '').localeCompare(b.label ?? ''))
  } else if (filters.sortMode === 'stock') {
    sorted.sort((a, b) => getTotalUnits(a) - getTotalUnits(b))
  }

  return sorted
}

/**
 * Total de unidades do lote ({@code unitsInStock + unitsInUse}). Falls back
 * para {@code totalUnits} quando o backend ja calcula.
 */
export function getTotalUnits(lot: ReagentLot) {
  if (typeof lot.totalUnits === 'number') return lot.totalUnits
  return (lot.unitsInStock ?? 0) + (lot.unitsInUse ?? 0)
}

/**
 * Estado visual derivado por lote para colorir cards. Pos refator v3:
 * {@code archived} bate em {@code inativo} (era {@code fora_de_estoque} no v2).
 */
export function getLotVisualState(lot: ReagentLot) {
  const daysLeft = lot.daysLeft ?? 999
  const expired = lot.status === 'vencido'
  const archived = lot.status === 'inativo'
  const urgent = !expired && !archived && daysLeft >= 0 && daysLeft <= 7
  const warning = !expired && !archived && daysLeft > 7 && daysLeft <= 30

  return {
    daysLeft,
    expired,
    archived,
    urgent,
    warning,
  }
}
