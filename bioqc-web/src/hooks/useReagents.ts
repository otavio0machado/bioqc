import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { reagentService } from '../services/reagentService'
import { userService } from '../services/userService'
import type {
  ArchiveReagentLotRequest,
  DeleteReagentLotRequest,
  ReagentLotRequest,
  StockMovementRequest,
  UnarchiveReagentLotRequest,
} from '../types'

const REAGENT_KEYS = {
  lots: ['reagents'] as const,
  labels: ['reagent-labels'] as const,
  dashboard: ['dashboard'] as const,
}

function invalidateReagents(queryClient: ReturnType<typeof useQueryClient>) {
  void queryClient.invalidateQueries({ queryKey: REAGENT_KEYS.lots })
  void queryClient.invalidateQueries({ queryKey: REAGENT_KEYS.labels })
  void queryClient.invalidateQueries({ queryKey: REAGENT_KEYS.dashboard })
}

export function useReagentLots(category?: string, status?: string) {
  return useQuery({
    queryKey: ['reagents', category, status],
    queryFn: () => reagentService.getLots(category, status),
  })
}

/**
 * Carrega o agregado de etiquetas para alimentar:
 * - O combobox de etiqueta no {@code ReagentLotModal}
 * - A visao "tags" da {@code ReagentesTab}
 */
export function useReagentLabels(enabled = true) {
  return useQuery({
    queryKey: REAGENT_KEYS.labels,
    queryFn: () => reagentService.getLabelSummaries(),
    enabled,
  })
}

/**
 * Lista de responsaveis (FUNCIONARIO + ADMIN ativos) para combobox em
 * arquivamento (refator v3, decisao 1.5).
 */
export function useResponsibles(enabled = true) {
  return useQuery({
    queryKey: ['responsibles'],
    queryFn: () => userService.getResponsibles(),
    enabled,
  })
}

export function useCreateReagentLot() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: ReagentLotRequest) => reagentService.createLot(request),
    // Conflitos de negocio (ex: lote duplicado) nao melhoram com retry.
    retry: false,
    onSuccess: () => invalidateReagents(queryClient),
  })
}

export function useUpdateReagentLot() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: ReagentLotRequest }) =>
      reagentService.updateLot(id, request),
    retry: false,
    onSuccess: () => invalidateReagents(queryClient),
  })
}

/**
 * Hard delete (refator v3, ADMIN-only). Body com {@code confirmLotNumber}
 * exato. Backend cascade movements + grava snapshot em audit_log.
 */
export function useDeleteReagentLot() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: DeleteReagentLotRequest }) =>
      reagentService.deleteLot(id, request),
    retry: false,
    onSuccess: () => invalidateReagents(queryClient),
  })
}

/**
 * Arquiva lote como {@code inativo}. Body exige data + responsavel (username).
 */
export function useArchiveReagentLot() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: ArchiveReagentLotRequest }) =>
      reagentService.archiveLot(id, request),
    retry: false,
    onSuccess: () => invalidateReagents(queryClient),
  })
}

/**
 * Reativa lote inativo. Backend re-deriva status (validade x estoque x abertura).
 */
export function useUnarchiveReagentLot() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request?: UnarchiveReagentLotRequest }) =>
      reagentService.unarchiveLot(id, request ?? {}),
    retry: false,
    onSuccess: () => invalidateReagents(queryClient),
  })
}

export function useReagentMovements(lotId?: string) {
  return useQuery({
    queryKey: ['reagent-movements', lotId],
    queryFn: () => reagentService.getMovements(lotId ?? ''),
    enabled: Boolean(lotId),
  })
}

export function useCreateStockMovement(lotId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: StockMovementRequest) => reagentService.createMovement(lotId, request),
    retry: false,
    onSuccess: () => {
      invalidateReagents(queryClient)
      void queryClient.invalidateQueries({ queryKey: ['reagent-movements', lotId] })
    },
  })
}
