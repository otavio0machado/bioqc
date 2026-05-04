import {
  AlertTriangle,
  CalendarClock,
  CheckCircle2,
  Clock,
  History,
  Pencil,
  Search,
  Trash2,
  Wrench,
  X,
} from 'lucide-react'
import { useMemo, useState, type Dispatch, type SetStateAction } from 'react'
import {
  useCreateMaintenanceRecord,
  useDeleteMaintenanceRecord,
  useMaintenanceRecords,
  useUpdateMaintenanceRecord,
} from '../../hooks/useMaintenance'
import type { MaintenanceRecord, MaintenanceRequest } from '../../types'
import {
  Button,
  Card,
  Combobox,
  EmptyState,
  Input,
  Modal,
  Select,
  StatCard,
  TextArea,
  useToast,
} from '../ui'
import type { ComboboxOption } from '../ui'
import { VoiceRecorderModal } from './VoiceRecorderModal'
import {
  compareLocalDate,
  diffInDays,
  formatLongBR,
  todayLocal,
} from '../../utils/date'

type DerivedStatus = 'ATRASADA' | 'PROXIMA' | 'AGENDADA' | 'EM_DIA'

const MAINTENANCE_TYPES = ['Preventiva', 'Corretiva', 'Calibração']

const emptyForm: MaintenanceRequest = {
  equipment: '',
  type: 'Preventiva',
  date: todayLocal(),
  nextDate: '',
  technician: '',
  notes: '',
}

/**
 * Deriva o status operacional de uma manutencao a partir da proxima data.
 *
 * - ATRASADA: nextDate < hoje
 * - PROXIMA: nextDate em ate 7 dias
 * - AGENDADA: nextDate em ate 30 dias
 * - EM_DIA: sem nextDate ou distante
 */
function deriveStatus(record: MaintenanceRecord): DerivedStatus {
  const today = todayLocal()
  if (!record.nextDate) return 'EM_DIA'
  const diff = diffInDays(today, record.nextDate)
  if (diff === null) return 'EM_DIA'
  if (diff < 0) return 'ATRASADA'
  if (diff <= 7) return 'PROXIMA'
  if (diff <= 30) return 'AGENDADA'
  return 'EM_DIA'
}

function statusLabel(status: DerivedStatus) {
  switch (status) {
    case 'ATRASADA':
      return 'Atrasada'
    case 'PROXIMA':
      return 'Próxima (7d)'
    case 'AGENDADA':
      return 'Agendada (30d)'
    default:
      return 'Em dia'
  }
}

function statusClasses(status: DerivedStatus) {
  switch (status) {
    case 'ATRASADA':
      return 'bg-red-100 text-red-800'
    case 'PROXIMA':
      return 'bg-amber-100 text-amber-800'
    case 'AGENDADA':
      return 'bg-amber-50 text-amber-700'
    default:
      return 'bg-green-100 text-green-800'
  }
}

function StatusPill({ status }: { status: DerivedStatus }) {
  return (
    <span className={'inline-flex items-center rounded-full px-3 py-1.5 text-sm font-semibold ' + statusClasses(status)}>
      {statusLabel(status)}
    </span>
  )
}

export function ManutencaoTab() {
  const { toast } = useToast()
  const { data: records = [] } = useMaintenanceRecords()
  const createRecord = useCreateMaintenanceRecord()
  const updateRecord = useUpdateMaintenanceRecord()
  const deleteRecord = useDeleteMaintenanceRecord()

  const [isModalOpen, setIsModalOpen] = useState(false)
  const [editingRecord, setEditingRecord] = useState<MaintenanceRecord | null>(null)
  const [form, setForm] = useState<MaintenanceRequest>({ ...emptyForm })

  // Filtros
  const [searchTerm, setSearchTerm] = useState('')
  const [typeFilter, setTypeFilter] = useState('')
  const [equipmentFilter, setEquipmentFilter] = useState('')
  const [technicianFilter, setTechnicianFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState<'todas' | DerivedStatus>('todas')

  // Undo delete
  const [deletedRecord, setDeletedRecord] = useState<{ request: MaintenanceRequest; timeout: number } | null>(null)

  // Historico por equipamento
  const [historyEquipment, setHistoryEquipment] = useState<string | null>(null)

  // Opcoes para o Combobox de equipamento / tecnico — derivadas dos records
  const equipmentOptions = useMemo<ComboboxOption[]>(() => {
    const map = new Map<string, number>()
    for (const r of records) {
      const name = r.equipment?.trim()
      if (!name) continue
      map.set(name, (map.get(name) ?? 0) + 1)
    }
    return Array.from(map.entries())
      .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
      .map(([name, count]) => ({
        value: name,
        label: name,
        description: count > 1 ? `${count} registros` : undefined,
      }))
  }, [records])

  const technicianOptions = useMemo<ComboboxOption[]>(() => {
    const set = new Map<string, number>()
    for (const r of records) {
      const t = r.technician?.trim()
      if (!t) continue
      set.set(t, (set.get(t) ?? 0) + 1)
    }
    return Array.from(set.entries())
      .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
      .map(([t, c]) => ({ value: t, label: t, description: c > 1 ? `${c} registros` : undefined }))
  }, [records])

  // KPIs — derivados da lista completa, independente dos filtros
  const kpis = useMemo(() => {
    let total = records.length
    let overdue = 0
    let next7 = 0
    let scheduled = 0
    for (const r of records) {
      const s = deriveStatus(r)
      if (s === 'ATRASADA') overdue++
      else if (s === 'PROXIMA') next7++
      else if (s === 'AGENDADA') scheduled++
    }
    return { total, overdue, next7, scheduled }
  }, [records])

  // Registros filtrados
  const filteredRecords = useMemo(() => {
    const term = searchTerm.trim().toLowerCase()
    return records
      .filter((r) => {
        if (typeFilter && r.type !== typeFilter) return false
        if (equipmentFilter && r.equipment !== equipmentFilter) return false
        if (technicianFilter && r.technician !== technicianFilter) return false
        if (statusFilter !== 'todas' && deriveStatus(r) !== statusFilter) return false
        if (term) {
          const hay = `${r.equipment ?? ''} ${r.type ?? ''} ${r.technician ?? ''} ${r.notes ?? ''}`.toLowerCase()
          if (!hay.includes(term)) return false
        }
        return true
      })
      .sort((a, b) => compareLocalDate(b.date, a.date))
  }, [records, typeFilter, equipmentFilter, technicianFilter, statusFilter, searchTerm])

  const handleOpenCreate = () => {
    setEditingRecord(null)
    setForm({ ...emptyForm, date: todayLocal() })
    setIsModalOpen(true)
  }

  const handleOpenEdit = (record: MaintenanceRecord) => {
    setEditingRecord(record)
    setForm({
      equipment: record.equipment,
      type: record.type,
      date: typeof record.date === 'string' ? record.date : new Date(record.date).toISOString().slice(0, 10),
      nextDate: record.nextDate ? (typeof record.nextDate === 'string' ? record.nextDate : new Date(record.nextDate).toISOString().slice(0, 10)) : '',
      technician: record.technician ?? '',
      notes: record.notes ?? '',
    })
    setIsModalOpen(true)
  }

  const handleCloseModal = () => {
    setIsModalOpen(false)
    setEditingRecord(null)
    setForm({ ...emptyForm })
  }

  const handleSave = async () => {
    if (!form.equipment || !form.type) {
      toast.warning('Preencha equipamento e tipo de manutenção.')
      return
    }
    if (form.nextDate && form.date && form.nextDate <= form.date) {
      toast.warning('A próxima data de manutenção deve ser posterior à data da manutenção atual.')
      return
    }
    const payload: MaintenanceRequest = {
      ...form,
      nextDate: form.nextDate || undefined,
    }
    try {
      if (editingRecord) {
        await updateRecord.mutateAsync({ id: editingRecord.id, request: payload })
        toast.success('Manutenção atualizada.')
      } else {
        await createRecord.mutateAsync(payload)
        toast.success('Manutenção registrada.')
      }
      handleCloseModal()
    } catch {
      toast.error(editingRecord ? 'Não foi possível atualizar a manutenção.' : 'Não foi possível registrar a manutenção.')
    }
  }

  const handleDelete = async (record: MaintenanceRecord) => {
    try {
      await deleteRecord.mutateAsync(record.id)
      // Ativa undo: guarda dados + agenda limpeza em 5s
      if (deletedRecord) {
        window.clearTimeout(deletedRecord.timeout)
      }
      // QA P0: normaliza datas antes de snapshotar. record.date pode vir como
      // Date (em cache) dependendo do parser — serializar como LocalDate puro
      // (YYYY-MM-DD local) evita shift UTC no re-create via undo.
      const normalizeDate = (v: unknown): string | undefined => {
        if (v == null || v === '') return undefined
        if (typeof v === 'string') return v
        if (v instanceof Date) {
          const yyyy = v.getFullYear()
          const mm = String(v.getMonth() + 1).padStart(2, '0')
          const dd = String(v.getDate()).padStart(2, '0')
          return `${yyyy}-${mm}-${dd}`
        }
        return String(v)
      }
      const snapshot: MaintenanceRequest = {
        equipment: record.equipment,
        type: record.type,
        date: normalizeDate(record.date) ?? todayLocal(),
        nextDate: normalizeDate(record.nextDate),
        technician: record.technician ?? undefined,
        notes: record.notes ?? undefined,
      }
      const timeout = window.setTimeout(() => setDeletedRecord(null), 5000)
      setDeletedRecord({ request: snapshot, timeout })
      toast.success('Manutenção excluída.')
    } catch {
      toast.error('Não foi possível excluir a manutenção.')
    }
  }

  const handleUndo = async () => {
    if (!deletedRecord) return
    window.clearTimeout(deletedRecord.timeout)
    try {
      await createRecord.mutateAsync(deletedRecord.request)
      toast.success('Manutenção restaurada.')
    } catch {
      toast.error('Não foi possível restaurar a manutenção.')
    } finally {
      setDeletedRecord(null)
    }
  }

  const clearFilters = () => {
    setSearchTerm('')
    setTypeFilter('')
    setEquipmentFilter('')
    setTechnicianFilter('')
    setStatusFilter('todas')
  }

  const hasActiveFilter =
    Boolean(searchTerm) || Boolean(typeFilter) || Boolean(equipmentFilter) || Boolean(technicianFilter) || statusFilter !== 'todas'

  if (!records.length) {
    return (
      <>
        <EmptyState
          icon={<Wrench className="h-8 w-8" />}
          title="Nenhuma manutenção cadastrada"
          description="Registre revisões preventivas, corretivas e calibrações para manter a operação rastreável."
          action={{ label: 'Nova Manutenção', onClick: handleOpenCreate }}
        />
        <MaintenanceModal
          form={form}
          isOpen={isModalOpen}
          isEditing={Boolean(editingRecord)}
          isSaving={editingRecord ? updateRecord.isPending : createRecord.isPending}
          onClose={handleCloseModal}
          onSave={handleSave}
          setForm={setForm}
          equipmentOptions={equipmentOptions}
          technicianOptions={technicianOptions}
        />
      </>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-end gap-2">
        <Button onClick={handleOpenCreate}>Nova Manutenção</Button>
      </div>

      {/* KPIs — clicaveis para pre-filtrar status */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <button type="button" onClick={() => setStatusFilter('todas')} className="text-left">
          <StatCard
            label="Total de registros"
            value={kpis.total}
            icon={<Wrench className="h-5 w-5" />}
            iconColor="bg-neutral-500"
          />
        </button>
        <button type="button" onClick={() => setStatusFilter('ATRASADA')} className="text-left">
          <StatCard
            label="Atrasadas"
            value={kpis.overdue}
            icon={<AlertTriangle className="h-5 w-5" />}
            iconColor={kpis.overdue > 0 ? 'bg-red-500' : 'bg-neutral-400'}
          />
        </button>
        <button type="button" onClick={() => setStatusFilter('PROXIMA')} className="text-left">
          <StatCard
            label="Próximas 7 dias"
            value={kpis.next7}
            icon={<Clock className="h-5 w-5" />}
            iconColor={kpis.next7 > 0 ? 'bg-amber-500' : 'bg-neutral-400'}
          />
        </button>
        <button type="button" onClick={() => setStatusFilter('AGENDADA')} className="text-left">
          <StatCard
            label="Agendadas 30 dias"
            value={kpis.scheduled}
            icon={<CalendarClock className="h-5 w-5" />}
            iconColor="bg-green-600"
          />
        </button>
      </div>

      {/* Filtros */}
      <Card className="space-y-4">
        <div className="grid gap-3 md:grid-cols-2 lg:grid-cols-4">
          <Input
            label="Busca"
            placeholder="Equipamento, tipo, técnico, nota..."
            icon={<Search className="h-4 w-4" />}
            value={searchTerm}
            onChange={(event) => setSearchTerm(event.target.value)}
          />
          <Select label="Tipo" value={typeFilter} onChange={(event) => setTypeFilter(event.target.value)}>
            <option value="">Todos os tipos</option>
            {MAINTENANCE_TYPES.map((t) => (
              <option key={t} value={t}>{t}</option>
            ))}
          </Select>
          <Combobox
            label="Equipamento"
            placeholder="Todos os equipamentos"
            value={equipmentFilter}
            onChange={setEquipmentFilter}
            options={equipmentOptions}
            allowCustom={false}
            emptyText="Nenhum equipamento cadastrado"
          />
          <Combobox
            label="Técnico"
            placeholder="Todos os técnicos"
            value={technicianFilter}
            onChange={setTechnicianFilter}
            options={technicianOptions}
            allowCustom={false}
            emptyText="Nenhum técnico cadastrado"
          />
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-sm text-neutral-500">Status:</span>
          {(['todas', 'ATRASADA', 'PROXIMA', 'AGENDADA', 'EM_DIA'] as const).map((s) => {
            const active = statusFilter === s
            return (
              <button
                key={s}
                type="button"
                onClick={() => setStatusFilter(s)}
                className={
                  'rounded-full px-3 py-1.5 text-sm font-medium transition ' +
                  (active
                    ? 'bg-green-700 text-white'
                    : 'bg-neutral-100 text-neutral-600 hover:bg-neutral-200')
                }
              >
                {s === 'todas' ? 'Todas' : statusLabel(s)}
              </button>
            )
          })}
          {hasActiveFilter ? (
            <button type="button" onClick={clearFilters} className="ml-auto inline-flex items-center gap-1 text-sm text-neutral-500 hover:text-neutral-700">
              <X className="h-3.5 w-3.5" /> Limpar filtros
            </button>
          ) : null}
        </div>
      </Card>

      {/* Lista */}
      {filteredRecords.length === 0 ? (
        <Card>
          <EmptyState
            icon={<Wrench className="h-8 w-8" />}
            title="Nenhuma manutenção encontrada"
            description="Ajuste busca, tipo ou status para ver mais registros."
          />
        </Card>
      ) : (
        <div className="grid gap-4 lg:grid-cols-2">
          {filteredRecords.map((record) => {
            const status = deriveStatus(record)
            const nextDiff = record.nextDate ? diffInDays(todayLocal(), record.nextDate) : null
            return (
              <Card key={record.id} className="space-y-4">
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0">
                    <button
                      type="button"
                      onClick={() => setHistoryEquipment(record.equipment)}
                      className="block truncate text-left text-base font-semibold text-green-900 underline-offset-2 hover:underline focus:outline-none focus:underline"
                      title={`Ver histórico de ${record.equipment}`}
                    >
                      {record.equipment}
                    </button>
                    <div className="text-sm text-neutral-500">{record.type}</div>
                  </div>
                  <div className="flex items-center gap-2">
                    <StatusPill status={status} />
                    <button
                      type="button"
                      onClick={() => handleOpenEdit(record)}
                      className="rounded-lg p-1.5 text-neutral-400 transition-colors hover:bg-neutral-100 hover:text-neutral-600"
                      title="Editar"
                    >
                      <Pencil className="h-4 w-4" />
                    </button>
                    <button
                      type="button"
                      onClick={() => handleDelete(record)}
                      className="rounded-lg p-1.5 text-neutral-400 transition-colors hover:bg-red-50 hover:text-red-500"
                      title="Excluir"
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </div>
                </div>
                <div className="grid gap-3 sm:grid-cols-2">
                  <div className="rounded-2xl bg-neutral-50 px-4 py-3">
                    <div className="text-xs uppercase tracking-wide text-neutral-400">Data</div>
                    <div className="mt-1 text-sm font-medium text-neutral-900">{formatLongBR(record.date)}</div>
                  </div>
                  <div className="rounded-2xl bg-neutral-50 px-4 py-3">
                    <div className="text-xs uppercase tracking-wide text-neutral-400">Próxima</div>
                    <div className="mt-1 text-sm font-medium text-neutral-900">
                      {record.nextDate ? formatLongBR(record.nextDate) : 'Sem previsão'}
                      {nextDiff !== null ? (
                        <span className={'ml-2 text-xs ' + (nextDiff < 0 ? 'text-red-600' : nextDiff <= 7 ? 'text-amber-600' : 'text-neutral-400')}>
                          {nextDiff < 0 ? `${Math.abs(nextDiff)}d atraso` : nextDiff === 0 ? 'hoje' : `em ${nextDiff}d`}
                        </span>
                      ) : null}
                    </div>
                  </div>
                </div>
                <div className="flex items-center gap-2 text-sm text-neutral-500">
                  <CalendarClock className="h-4 w-4" />
                  {record.technician || 'Técnico não informado'}
                </div>
                {record.notes ? <div className="text-sm text-neutral-600">{record.notes}</div> : null}
              </Card>
            )
          })}
        </div>
      )}

      {/* Modal cadastro/edicao */}
      <MaintenanceModal
        form={form}
        isOpen={isModalOpen}
        isEditing={Boolean(editingRecord)}
        isSaving={editingRecord ? updateRecord.isPending : createRecord.isPending}
        onClose={handleCloseModal}
        onSave={handleSave}
        setForm={setForm}
        equipmentOptions={equipmentOptions}
        technicianOptions={technicianOptions}
      />

      {/* Modal historico por equipamento */}
      <EquipmentHistoryModal
        equipment={historyEquipment}
        records={records}
        onClose={() => setHistoryEquipment(null)}
      />

      {/* Undo delete — desabilita enquanto createRecord esta rodando (QA P1) */}
      {deletedRecord ? (
        <div className="fixed bottom-4 right-4 z-50 flex items-center gap-3 rounded-2xl border border-amber-200 bg-white px-4 py-3 shadow-lg">
          <span className="text-base text-neutral-700">Manutenção excluída.</span>
          <button
            onClick={handleUndo}
            disabled={createRecord.isPending}
            className="font-semibold text-green-700 hover:text-green-800 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {createRecord.isPending ? 'Restaurando...' : 'Desfazer'}
          </button>
        </div>
      ) : null}
    </div>
  )
}

interface MaintenanceModalProps {
  form: MaintenanceRequest
  isOpen: boolean
  isEditing: boolean
  isSaving: boolean
  onClose: () => void
  onSave: () => void
  setForm: Dispatch<SetStateAction<MaintenanceRequest>>
  equipmentOptions: ComboboxOption[]
  technicianOptions: ComboboxOption[]
}

function MaintenanceModal({
  form,
  isOpen,
  isEditing,
  isSaving,
  onClose,
  onSave,
  setForm,
  equipmentOptions,
  technicianOptions,
}: MaintenanceModalProps) {
  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={isEditing ? 'Editar manutenção' : 'Nova manutenção'}
      footer={
        <div className="flex justify-end gap-3">
          <Button variant="ghost" onClick={onClose}>Cancelar</Button>
          <Button onClick={onSave} loading={isSaving}>{isEditing ? 'Atualizar' : 'Salvar'}</Button>
        </div>
      }
    >
      <div className="mb-4 flex justify-end">
        <VoiceRecorderModal
          formType="manutencao"
          title="Preencher manutenção por voz"
          onApply={(data) =>
            setForm((current) => ({
              ...current,
              equipment: typeof data.equipment === 'string' ? data.equipment : current.equipment,
              type: typeof data.type === 'string' ? data.type : current.type,
              date: typeof data.date === 'string' ? data.date : current.date,
              nextDate: typeof data.next_date === 'string' ? data.next_date : current.nextDate,
              notes: typeof data.notes === 'string' ? data.notes : current.notes,
            }))
          }
        />
      </div>
      <div className="grid gap-4 md:grid-cols-2">
        <Combobox
          label="Equipamento"
          placeholder="Busque ou digite um novo equipamento"
          value={form.equipment}
          onChange={(next) => setForm((current) => ({ ...current, equipment: next }))}
          options={equipmentOptions}
          allowCustom
          createLabel="Cadastrar novo"
          emptyText="Nenhum equipamento cadastrado — digite para criar"
        />
        <Select label="Tipo" value={form.type} onChange={(event) => setForm((current) => ({ ...current, type: event.target.value }))}>
          {MAINTENANCE_TYPES.map((item) => (
            <option key={item} value={item}>
              {item}
            </option>
          ))}
        </Select>
        <Input label="Data" type="date" value={form.date} onChange={(event) => setForm((current) => ({ ...current, date: event.target.value }))} />
        <Input label="Próxima data" type="date" value={form.nextDate ?? ''} onChange={(event) => setForm((current) => ({ ...current, nextDate: event.target.value }))} />
        <Combobox
          label="Técnico"
          placeholder="Busque ou digite o técnico"
          value={form.technician ?? ''}
          onChange={(next) => setForm((current) => ({ ...current, technician: next }))}
          options={technicianOptions}
          allowCustom
          createLabel="Cadastrar novo"
          emptyText="Nenhum técnico cadastrado — digite para criar"
        />
      </div>
      <div className="mt-4">
        <TextArea label="Notas" value={form.notes} onChange={(event) => setForm((current) => ({ ...current, notes: event.target.value }))} />
      </div>
    </Modal>
  )
}

interface EquipmentHistoryModalProps {
  equipment: string | null
  records: MaintenanceRecord[]
  onClose: () => void
}

/**
 * Historico completo de um equipamento, aberto ao clicar no nome do
 * equipamento na lista. Exibe todas as manutencoes em ordem cronologica
 * e destaca a proxima prevista.
 */
function EquipmentHistoryModal({ equipment, records, onClose }: EquipmentHistoryModalProps) {
  const isOpen = equipment !== null

  const { list, upcoming } = useMemo(() => {
    const list = records
      .filter((r) => r.equipment === equipment)
      .sort((a, b) => compareLocalDate(b.date, a.date))
    // Proxima prevista = a `nextDate` mais cedo que ainda nao passou
    const today = todayLocal()
    const upcoming = list
      .filter((r) => r.nextDate && compareLocalDate(r.nextDate, today) >= 0)
      .sort((a, b) => compareLocalDate(a.nextDate, b.nextDate))[0]
    return { list, upcoming }
  }, [records, equipment])

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={equipment ? `Histórico — ${equipment}` : ''}
      size="lg"
    >
      {!isOpen ? null : (
        <div className="space-y-4">
          {upcoming ? (
            <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4">
              <div className="flex items-center gap-2 text-sm font-semibold text-amber-900">
                <Clock className="h-4 w-4" /> Próxima manutenção prevista
              </div>
              <div className="mt-1 text-base font-medium text-amber-950">
                {formatLongBR(upcoming.nextDate ?? '')} · {upcoming.type}
              </div>
            </div>
          ) : (
            <div className="rounded-2xl border border-green-200 bg-green-50 p-4 text-sm text-green-900">
              <div className="flex items-center gap-2 font-semibold">
                <CheckCircle2 className="h-4 w-4" /> Sem manutenção futura agendada
              </div>
              <div className="mt-1 text-green-900/80">
                Todos os registros deste equipamento estão concluídos ou sem previsão.
              </div>
            </div>
          )}

          {list.length === 0 ? (
            <EmptyState
              icon={<History className="h-8 w-8" />}
              title="Sem histórico"
              description={`Nenhuma manutenção registrada para ${equipment}.`}
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-neutral-100 text-xs uppercase tracking-wider text-neutral-500">
                    <th className="px-3 py-2.5">Data</th>
                    <th className="px-3 py-2.5">Tipo</th>
                    <th className="px-3 py-2.5">Próxima</th>
                    <th className="px-3 py-2.5">Técnico</th>
                    <th className="px-3 py-2.5">Notas</th>
                  </tr>
                </thead>
                <tbody>
                  {list.map((record) => (
                    <tr key={record.id} className="border-b border-neutral-50 hover:bg-neutral-50/50">
                      <td className="whitespace-nowrap px-3 py-2 text-neutral-700">{formatLongBR(record.date)}</td>
                      <td className="px-3 py-2 text-neutral-700">{record.type}</td>
                      <td className="px-3 py-2 text-neutral-600">{record.nextDate ? formatLongBR(record.nextDate) : '—'}</td>
                      <td className="px-3 py-2 text-neutral-500">{record.technician || '—'}</td>
                      <td className="px-3 py-2 text-neutral-500">{record.notes || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </Modal>
  )
}

