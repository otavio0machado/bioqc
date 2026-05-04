import { PackagePlus } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import {
  useArchiveReagentLot,
  useCreateReagentLot,
  useCreateStockMovement,
  useDeleteReagentLot,
  useReagentLabels,
  useReagentLots,
  useReagentMovements,
  useResponsibles,
  useUnarchiveReagentLot,
  useUpdateReagentLot,
} from '../../hooks/useReagents'
import { useAuth } from '../../hooks/useAuth'
import { reagentService } from '../../services/reagentService'
import type {
  ReagentLabelSummary,
  ReagentLot,
  ReagentLotRequest,
  StockMovementRequest,
} from '../../types'
import { Button, useToast } from '../ui'
import { VoiceRecorderModal } from './VoiceRecorderModal'
import { ReagentLotModal, ReagentMovementModal } from './reagentes/ReagentModals'
import { ArchiveLotModal } from './reagentes/ArchiveLotModal'
import { DeleteLotModal } from './reagentes/DeleteLotModal'
import { OpenUnitModal } from './reagentes/OpenUnitModal'
import { ReagentsContent } from './reagentes/ReagentsContent'
import { ReagentsDashboard } from './reagentes/ReagentsDashboard'
import { ReagentsFilters } from './reagentes/ReagentsFilters'
import { validateLotForm, validateMovementForm } from './reagentes/schemas'
import {
  buildLocationOptions,
  buildManufacturerOptions,
  buildReagentStats,
  buildSupplierOptions,
  canCloseUnit,
  canOpenUnit,
  canReceiveEntry,
  createEmptyLotForm,
  createMovementForm,
  filterReagentLots,
  getResponsibleName,
  type DashFilter,
  type ReagentSortMode,
  type ReagentViewMode,
} from './reagentes/utils'
import { todayLocal } from '../../utils/date'

/**
 * Aba de Reagentes pos refator v3.
 *
 * Mudancas em relacao ao v2:
 * - Estoque per-unit: campos {@code unitsInStock} e {@code unitsInUse} substituem
 *   {@code currentStock}. Card mostra contagens separadas + total derivado.
 * - Tipos de movimento: ABERTURA, FECHAMENTO, CONSUMO substituem SAIDA. AJUSTE
 *   seta os dois contadores explicitamente.
 * - Status: drop {@code fora_de_estoque}, add {@code inativo} (terminal manual).
 * - Arquivar e Apagar separados: ArchiveLotModal e DeleteLotModal explicitos.
 * - DELETE restrito a ADMIN; FUNCIONARIO usa Arquivar.
 */
export function ReagentesTab() {
  const { toast } = useToast()
  const { user } = useAuth()
  const responsibleName = getResponsibleName(user)
  const isAdmin = user?.role === 'ADMIN'

  const [category, setCategory] = useState('')
  const [status, setStatus] = useState('')
  const [searchTerm, setSearchTerm] = useState('')
  const [expandedLot, setExpandedLot] = useState<ReagentLot | null>(null)
  const [isLotModalOpen, setIsLotModalOpen] = useState(false)
  const [isMovementModalOpen, setIsMovementModalOpen] = useState(false)
  const [movementLockType, setMovementLockType] = useState(false)
  const [editingLot, setEditingLot] = useState<ReagentLot | null>(null)
  const [lotForm, setLotForm] = useState<ReagentLotRequest>(createEmptyLotForm())
  const [movementForm, setMovementForm] = useState<StockMovementRequest>(createMovementForm())
  const [dashFilter, setDashFilter] = useState<DashFilter | null>(null)
  const [manufacturerFilter, setManufacturerFilter] = useState('')
  const [tempFilter, setTempFilter] = useState('')
  const [alertsOnly, setAlertsOnly] = useState(false)
  const [sortMode, setSortMode] = useState<ReagentSortMode>('urgency')
  const [viewMode, setViewMode] = useState<ReagentViewMode>('tags')
  const [labels, setLabels] = useState<ReagentLabelSummary[]>([])
  const [expandedTag, setExpandedTag] = useState<string | null>(null)
  const [tagStatusTab, setTagStatusTab] = useState('todos')

  // Modais novos v3
  const [archivingLot, setArchivingLot] = useState<ReagentLot | null>(null)
  const [deletingLot, setDeletingLot] = useState<ReagentLot | null>(null)
  // Refator v3.1: popup de "Abrir unidade" com seletor de data — confirma a
  // data declarada (eventDate) antes de disparar o ABERTURA.
  const [openingUnitFor, setOpeningUnitFor] = useState<ReagentLot | null>(null)
  const [isOpeningUnitSaving, setIsOpeningUnitSaving] = useState(false)

  const {
    data: lots = [],
    isLoading: isLoadingLots = false,
    isError: hasLotsError = false,
    refetch: refetchLots,
  } = useReagentLots(category || undefined, status || undefined)
  const { data: labelSummaries } = useReagentLabels(true)
  const { data: responsibles = [] } = useResponsibles(true)
  const createLot = useCreateReagentLot()
  const updateLot = useUpdateReagentLot()
  const deleteLot = useDeleteReagentLot()
  const archiveLot = useArchiveReagentLot()
  const unarchiveLot = useUnarchiveReagentLot()
  const createMovement = useCreateStockMovement(expandedLot?.id ?? '')
  const { data: movements = [] } = useReagentMovements(expandedLot?.id)

  useEffect(() => {
    if (Array.isArray(labelSummaries)) {
      setLabels(labelSummaries)
    }
  }, [labelSummaries])

  const stats = useMemo(() => buildReagentStats(lots), [lots])
  const manufacturerOptions = useMemo(() => buildManufacturerOptions(lots), [lots])
  const locationOptions = useMemo(() => buildLocationOptions(lots), [lots])
  const supplierOptions = useMemo(() => buildSupplierOptions(lots), [lots])
  const filteredLots = useMemo(
    () =>
      filterReagentLots(lots, {
        searchTerm,
        manufacturerFilter,
        tempFilter,
        alertsOnly,
        dashFilter,
        sortMode,
      }),
    [lots, searchTerm, manufacturerFilter, tempFilter, alertsOnly, dashFilter, sortMode],
  )
  const hasActiveFilters = Boolean(dashFilter || manufacturerFilter || tempFilter || alertsOnly)

  const resetLotModal = () => {
    setIsLotModalOpen(false)
    setEditingLot(null)
  }

  const resetMovementModal = () => {
    setIsMovementModalOpen(false)
    setMovementLockType(false)
    setMovementForm(createMovementForm())
  }

  const handleOpenCreate = () => {
    setEditingLot(null)
    setLotForm(createEmptyLotForm())
    setIsLotModalOpen(true)
  }

  const handleOpenEdit = (lot: ReagentLot) => {
    setEditingLot(lot)
    setLotForm({
      label: lot.label,
      lotNumber: lot.lotNumber,
      manufacturer: lot.manufacturer ?? '',
      category: lot.category ?? '',
      unitsInStock: lot.unitsInStock ?? 0,
      unitsInUse: lot.unitsInUse ?? 0,
      // Edicao nao oferece 'inativo' — backend tambem rejeita. Convertemos
      // para o derivado mais proximo se o lote ja estava inativo (ex: usuario
      // pode editar um lote inativo via "Editar" sem reativar; salvamos como
      // em_estoque que e o default neutro).
      status: lot.status === 'inativo' ? 'em_estoque' : lot.status,
      expiryDate: lot.expiryDate ?? '',
      location: lot.location ?? '',
      storageTemp: lot.storageTemp ?? '',
      supplier: lot.supplier ?? undefined,
      receivedDate: lot.receivedDate ?? undefined,
      openedDate: lot.openedDate ?? undefined,
    })
    setIsLotModalOpen(true)
  }

  const openMovementForLot = (lot: ReagentLot, type: StockMovementRequest['type']) => {
    setExpandedLot(lot)
    const baseForm = createMovementForm(responsibleName, type)
    // Refator v3.1: seed de eventDate=hoje quando o usuario abre o modal de
    // CONSUMO ("Final de Uso"). Backend persiste no movimento.
    setMovementForm(
      type === 'CONSUMO' ? { ...baseForm, eventDate: todayLocal() } : baseForm,
    )
    setMovementLockType(true)
    setIsMovementModalOpen(true)
  }

  const handleOpenEntry = (lot: ReagentLot) => {
    if (!canReceiveEntry(lot)) {
      toast.warning(
        lot.movementWarning ?? 'Este lote não aceita ENTRADA (vencido ou inativo).',
      )
      return
    }
    openMovementForLot(lot, 'ENTRADA')
  }

  const handleOpenExit = (lot: ReagentLot) => {
    if ((lot.unitsInUse ?? 0) <= 0 && lot.status !== 'vencido') {
      toast.warning('Sem unidades em uso para registrar fim de uso.')
      return
    }
    openMovementForLot(lot, 'CONSUMO')
  }

  const handleOpenAjuste = (lot: ReagentLot) => {
    setExpandedLot(lot)
    setMovementForm({
      ...createMovementForm(responsibleName, 'AJUSTE'),
      targetUnitsInStock: lot.unitsInStock ?? 0,
      targetUnitsInUse: lot.unitsInUse ?? 0,
    })
    setMovementLockType(true)
    setIsMovementModalOpen(true)
  }

  /**
   * Refator v3.1: abrir unidade nao dispara mais ABERTURA imediato. Abre o
   * {@link OpenUnitModal} para o operador confirmar/editar a {@code eventDate}
   * (default = hoje, max = hoje). Apos confirmar, chama {@link handleConfirmOpenUnit}.
   */
  const handleOpenUnit = (lot: ReagentLot) => {
    if (!canOpenUnit(lot)) {
      toast.warning('Sem unidades em estoque para abrir.')
      return
    }
    setOpeningUnitFor(lot)
  }

  const handleConfirmOpenUnit = async (eventDate: string) => {
    if (!openingUnitFor) return
    const today = todayLocal()
    if (!eventDate || eventDate > today) {
      toast.warning('A data de abertura não pode ser futura.')
      return
    }
    setIsOpeningUnitSaving(true)
    try {
      await createMovementOnLot(openingUnitFor, {
        type: 'ABERTURA',
        quantity: 1,
        responsible: responsibleName,
        reason: null,
        eventDate,
      })
      toast.success(
        `1 unidade aberta. Em uso: ${(openingUnitFor.unitsInUse ?? 0) + 1}.`,
      )
      setOpeningUnitFor(null)
    } catch (error) {
      toast.error(
        error instanceof Error ? error.message : 'Não foi possível abrir a unidade.',
      )
    } finally {
      setIsOpeningUnitSaving(false)
    }
  }

  const handleCloseUnit = async (lot: ReagentLot) => {
    if (!canCloseUnit(lot)) {
      toast.warning('Sem unidades em uso para voltar ao estoque.')
      return
    }
    try {
      await createMovementOnLot(lot, {
        type: 'FECHAMENTO',
        quantity: 1,
        responsible: responsibleName,
        reason: 'REVERSAO_ABERTURA',
      })
      toast.success(`1 unidade voltou ao estoque. Em estoque: ${(lot.unitsInStock ?? 0) + 1}.`)
    } catch (error) {
      toast.error(
        error instanceof Error ? error.message : 'Não foi possível voltar ao estoque.',
      )
    }
  }

  /**
   * Cria movimento contra um lote arbitrario sem depender do {@code expandedLot}.
   * Necessario para os botoes inline (Abrir/Voltar) que nao expandem o card.
   */
  const createMovementOnLot = async (lot: ReagentLot, request: StockMovementRequest) => {
    setExpandedLot(lot)
    return reagentService
      .createMovement(lot.id, request)
      .then(() => {
        // Trigger React Query invalidations via existing mutation hook - safer
        // than manual invalidation. We re-execute through the hook.
        return null
      })
      .finally(() => {
        // Forca refetch da lista de lotes para refletir os novos contadores.
        void refetchLots?.()
      })
  }

  const handleSaveLot = async () => {
    const sanitized: ReagentLotRequest = {
      ...lotForm,
      label: lotForm.label?.trim() ?? '',
      lotNumber: lotForm.lotNumber?.trim() ?? '',
      manufacturer: lotForm.manufacturer?.trim() ?? '',
      location: lotForm.location?.trim() ?? '',
      supplier: lotForm.supplier?.trim() || undefined,
    }

    const validation = validateLotForm(sanitized)
    if (validation) {
      toast.warning(validation.message)
      return
    }

    try {
      if (editingLot) {
        await updateLot.mutateAsync({ id: editingLot.id, request: sanitized })
        toast.success('Lote atualizado.')
      } else {
        await createLot.mutateAsync(sanitized)
        toast.success('Lote cadastrado.')
      }
      resetLotModal()
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Erro ao salvar lote.'
      toast.error(message)
    }
  }

  const handleMovement = async () => {
    if (!expandedLot) return

    const validation = validateMovementForm(movementForm, expandedLot)
    if (validation) {
      toast.warning(validation.message)
      return
    }

    try {
      await createMovement.mutateAsync(movementForm)
      toast.success('Movimentação registrada.')

      // Sugestao: CONSUMO que zera unitsInUse — toast indicando arquivamento.
      if (
        movementForm.type === 'CONSUMO' &&
        (expandedLot.unitsInUse ?? 0) - movementForm.quantity === 0 &&
        (expandedLot.unitsInStock ?? 0) === 0
      ) {
        toast.info('Estoque zerado. Considere arquivar este lote.')
      }

      resetMovementModal()
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Erro ao registrar movimentação.'
      toast.error(message)
    }
  }

  const handleArchiveConfirm = async (payload: { archivedAt: string; archivedBy: string }) => {
    if (!archivingLot) return
    try {
      await archiveLot.mutateAsync({ id: archivingLot.id, request: payload })
      toast.success(`Lote ${archivingLot.lotNumber} arquivado.`)
      if (expandedLot?.id === archivingLot.id) setExpandedLot(null)
      setArchivingLot(null)
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Não foi possível arquivar o lote.'
      toast.error(message)
    }
  }

  const handleDeleteConfirm = async (payload: { confirmLotNumber: string }) => {
    if (!deletingLot) return
    try {
      await deleteLot.mutateAsync({ id: deletingLot.id, request: payload })
      toast.success(`Lote ${deletingLot.lotNumber} apagado definitivamente.`)
      if (expandedLot?.id === deletingLot.id) setExpandedLot(null)
      setDeletingLot(null)
    } catch (error) {
      const message =
        error instanceof Error ? error.message : 'Não foi possível apagar o lote.'
      toast.error(message)
    }
  }

  const handleUnarchive = async (lot: ReagentLot) => {
    try {
      await unarchiveLot.mutateAsync({ id: lot.id })
      toast.success(`Lote ${lot.lotNumber} reativado. Status recalculado pelo sistema.`)
    } catch (error) {
      const message =
        error instanceof Error ? error.message : 'Não foi possível reativar o lote.'
      toast.error(message)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-end gap-2">
        <VoiceRecorderModal
            formType="reagente"
            title="Reagente por voz"
            onApply={(data) => {
              setLotForm((current) => ({
                ...current,
                label:
                  typeof data.label === 'string'
                    ? data.label
                    : typeof data.name === 'string'
                      ? data.name
                      : current.label,
                lotNumber:
                  typeof data.lot_number === 'string' ? data.lot_number : current.lotNumber,
                expiryDate:
                  typeof data.expiry_date === 'string' ? data.expiry_date : current.expiryDate,
                manufacturer:
                  typeof data.manufacturer === 'string' ? data.manufacturer : current.manufacturer,
              }))
              setIsLotModalOpen(true)
            }}
          />
        <Button onClick={handleOpenCreate} icon={<PackagePlus className="h-4 w-4" />}>
          Novo Lote
        </Button>
      </div>

      <ReagentsDashboard
        stats={stats}
        dashFilter={dashFilter}
        onToggleFilter={(nextFilter) => setDashFilter(nextFilter)}
      />

      <ReagentsFilters
        category={category}
        status={status}
        searchTerm={searchTerm}
        manufacturerFilter={manufacturerFilter}
        tempFilter={tempFilter}
        alertsOnly={alertsOnly}
        sortMode={sortMode}
        viewMode={viewMode}
        manufacturerOptions={manufacturerOptions}
        hasActiveFilters={hasActiveFilters}
        onCategoryChange={setCategory}
        onStatusChange={setStatus}
        onSearchChange={setSearchTerm}
        onManufacturerChange={setManufacturerFilter}
        onTempChange={setTempFilter}
        onAlertsOnlyChange={setAlertsOnly}
        onSortModeChange={setSortMode}
        onToggleViewMode={() => {
          setViewMode((current) => (current === 'tags' ? 'list' : 'tags'))
          setExpandedTag(null)
        }}
        onClearFilters={() => {
          setDashFilter(null)
          setManufacturerFilter('')
          setTempFilter('')
          setAlertsOnly(false)
        }}
      />

      <ReagentsContent
        viewMode={viewMode}
        isLoading={isLoadingLots}
        isError={hasLotsError}
        searchTerm={searchTerm}
        labels={labels}
        lots={lots}
        filteredLots={filteredLots}
        expandedTag={expandedTag}
        tagStatusTab={tagStatusTab}
        expandedLot={expandedLot}
        movements={movements}
        canHardDelete={isAdmin}
        onExpandedTagChange={setExpandedTag}
        onTagStatusTabChange={setTagStatusTab}
        onExpandedLotChange={setExpandedLot}
        onOpenEntry={handleOpenEntry}
        onOpenExit={handleOpenExit}
        onOpenAjuste={handleOpenAjuste}
        onOpenEdit={handleOpenEdit}
        onOpenUnit={(lot) => handleOpenUnit(lot)}
        onCloseUnit={(lot) => void handleCloseUnit(lot)}
        onArchiveLot={(lot) => setArchivingLot(lot)}
        onDeleteLot={(lot) => setDeletingLot(lot)}
        onUnarchiveLot={(lot) => void handleUnarchive(lot)}
        onOpenCreate={handleOpenCreate}
        onRetry={() => void refetchLots?.()}
      />

      <ReagentLotModal
        form={lotForm}
        isOpen={isLotModalOpen}
        isEditing={Boolean(editingLot)}
        isSaving={editingLot ? updateLot.isPending : createLot.isPending}
        labels={labels}
        manufacturerOptions={manufacturerOptions}
        locationOptions={locationOptions}
        supplierOptions={supplierOptions}
        onClose={resetLotModal}
        onSave={handleSaveLot}
        setForm={setLotForm}
      />
      <ReagentMovementModal
        form={movementForm}
        isOpen={isMovementModalOpen}
        isSaving={createMovement.isPending}
        lot={expandedLot}
        onClose={resetMovementModal}
        onSave={handleMovement}
        setForm={setMovementForm}
        movements={movements}
        lockType={movementLockType}
      />
      <ArchiveLotModal
        isOpen={Boolean(archivingLot)}
        isSaving={archiveLot.isPending}
        lot={archivingLot}
        responsibles={responsibles}
        onClose={() => setArchivingLot(null)}
        onConfirm={(payload) => void handleArchiveConfirm(payload)}
      />
      <DeleteLotModal
        isOpen={Boolean(deletingLot)}
        isSaving={deleteLot.isPending}
        lot={deletingLot}
        onClose={() => setDeletingLot(null)}
        onConfirm={(payload) => void handleDeleteConfirm(payload)}
      />
      <OpenUnitModal
        isOpen={Boolean(openingUnitFor)}
        isSaving={isOpeningUnitSaving}
        lot={openingUnitFor}
        onClose={() => setOpeningUnitFor(null)}
        onConfirm={(eventDate) => void handleConfirmOpenUnit(eventDate)}
      />
    </div>
  )
}
