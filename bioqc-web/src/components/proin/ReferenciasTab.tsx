import { Pencil, Plus, Trash2 } from 'lucide-react'
import { useMemo, useState, type Dispatch, type SetStateAction } from 'react'
import {
  useCreateQcExam,
  useCreateQcReference,
  useDeleteQcReference,
  useQcExams,
  useQcReferences,
  useUpdateQcReference,
} from '../../hooks/useQcRecords'
import { qcService } from '../../services/qcService'
import type { QcExam, QcReferenceRequest, QcReferenceValue } from '../../types'
import { Button, Card, Combobox, EmptyState, Input, Modal, Select, useToast } from '../ui'
import type { ComboboxOption } from '../ui'

interface ReferenciasTabProps {
  area: string
}

const todayStr = () => new Date().toISOString().slice(0, 10)

const emptyReferenceForm: QcReferenceRequest = {
  examId: '',
  name: '',
  level: 'Normal',
  lotNumber: '',
  manufacturer: '',
  targetValue: 0,
  targetSd: 0,
  cvMaxThreshold: 10,
  validFrom: todayStr(),
  validUntil: '',
  notes: '',
}

export function ReferenciasTab({ area }: ReferenciasTabProps) {
  const { toast } = useToast()
  const { data: exams = [] } = useQcExams(area)
  const { data: references = [] } = useQcReferences(undefined, false)
  const createReference = useCreateQcReference()
  const updateReference = useUpdateQcReference()
  const deleteReference = useDeleteQcReference()

  const [isModalOpen, setIsModalOpen] = useState(false)
  const [editing, setEditing] = useState<QcReferenceValue | null>(null)
  const [form, setForm] = useState<QcReferenceRequest>(emptyReferenceForm)
  const [validityFilter, setValidityFilter] = useState<'todas' | 'vencidas' | 'validas'>('validas')

  // Sugestoes para o Combobox "Nome do Registro": nomes distintos ja cadastrados
  // na mesma area, usados tanto em create quanto em edit. allowCustom permite
  // digitar um novo nome sem sair do campo.
  const registroNameOptions = useMemo<ComboboxOption[]>(() => {
    const set = new Map<string, number>()
    references
      .filter((reference) => reference.exam?.area === area)
      .forEach((reference) => {
        const name = reference.name?.trim()
        if (!name) return
        set.set(name, (set.get(name) ?? 0) + 1)
      })
    return Array.from(set.entries())
      .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
      .map(([name, count]) => ({
        value: name,
        label: name,
        description: count > 1 ? `${count} referencias` : undefined,
      }))
  }, [references, area])

  const filteredReferences = useMemo(() => {
    const today = todayStr()
    return references
      .filter((reference) => reference.exam?.area === area)
      .filter((reference) => {
        if (validityFilter === 'todas') return true
        const from = reference.validFrom?.slice(0, 10)
        const until = reference.validUntil?.slice(0, 10)
        const isExpired = Boolean(until) && until! < today
        const isNotYetValid = Boolean(from) && from! > today
        const isValid = !isExpired && !isNotYetValid
        if (validityFilter === 'validas') return isValid
        if (validityFilter === 'vencidas') return isExpired
        return true
      })
  }, [area, references, validityFilter])

  const openCreate = () => {
    setEditing(null)
    setForm({ ...emptyReferenceForm, validFrom: todayStr() })
    setIsModalOpen(true)
  }

  const openEdit = (reference: QcReferenceValue) => {
    setEditing(reference)
    setForm({
      examId: reference.exam.id,
      name: reference.name,
      level: reference.level,
      lotNumber: reference.lotNumber ?? '',
      manufacturer: reference.manufacturer ?? '',
      targetValue: reference.targetValue,
      targetSd: reference.targetSd,
      cvMaxThreshold: reference.cvMaxThreshold,
      validFrom: reference.validFrom ?? '',
      validUntil: reference.validUntil ?? '',
      notes: reference.notes ?? '',
    })
    setIsModalOpen(true)
  }

  const handleSave = async () => {
    if (!form.examId || !form.name) {
      toast.warning('Preencha o nome do registro e selecione o exame.')
      return
    }
    const payload: QcReferenceRequest = {
      ...form,
      validFrom: form.validFrom || undefined,
      validUntil: form.validUntil || undefined,
      lotNumber: form.lotNumber || undefined,
      manufacturer: form.manufacturer || undefined,
      notes: form.notes || undefined,
    }
    try {
      if (editing) {
        await updateReference.mutateAsync({ id: editing.id, request: payload })
        toast.success('Referência atualizada.')
      } else {
        await createReference.mutateAsync(payload)
        toast.success('Referência criada.')
      }
      setIsModalOpen(false)
    } catch {
      toast.error('Não foi possível salvar a referência.')
    }
  }

  const handleDelete = async (id: string) => {
    try {
      await deleteReference.mutateAsync(id)
      toast.success('Referência excluída.')
    } catch {
      toast.error('Não foi possível excluir a referência.')
    }
  }

  const hasAnyReferenceInArea = references.some((reference) => reference.exam?.area === area)

  if (!hasAnyReferenceInArea) {
    return (
      <>
        <EmptyState
          icon={<Plus className="h-8 w-8" />}
          title="Nenhuma referência cadastrada"
          description="Cadastre valores alvo, desvio padrão e validade para automatizar o preenchimento do registro."
          action={{ label: 'Nova Referência', onClick: openCreate }}
        />
        <ReferenceModal
          area={area}
          exams={exams}
          registroNameOptions={registroNameOptions}
          form={form}
          editing={editing}
          isOpen={isModalOpen}
          onClose={() => setIsModalOpen(false)}
          onSave={handleSave}
          setForm={setForm}
          isSaving={createReference.isPending || updateReference.isPending}
        />
      </>
    )
  }

  const filterButtonClass = (active: boolean) =>
    `rounded-full px-3 py-1.5 text-sm font-medium transition ${
      active
        ? 'bg-green-700 text-white'
        : 'bg-neutral-100 text-neutral-600 hover:bg-neutral-200'
    }`

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 className="text-lg font-semibold text-neutral-900">Referências</h3>
          <p className="text-sm text-neutral-500">Faixas alvo ativas para a área de {area}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <button type="button" className={filterButtonClass(validityFilter === 'todas')} onClick={() => setValidityFilter('todas')}>
            Todas
          </button>
          <button type="button" className={filterButtonClass(validityFilter === 'vencidas')} onClick={() => setValidityFilter('vencidas')}>
            Vencidas
          </button>
          <button type="button" className={filterButtonClass(validityFilter === 'validas')} onClick={() => setValidityFilter('validas')}>
            Válidas
          </button>
          <Button onClick={openCreate} icon={<Plus className="h-4 w-4" />}>
            Nova Referência
          </Button>
        </div>
      </div>

      <Card>
        {filteredReferences.length === 0 ? (
          <div className="rounded-xl border border-neutral-200 bg-neutral-50 px-4 py-8 text-center text-base text-neutral-500">
            Nenhuma referência corresponde ao filtro selecionado.
          </div>
        ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-neutral-100 text-xs uppercase tracking-wider text-neutral-500">
                <th className="px-3 py-2.5">Nome</th>
                <th className="px-3 py-2.5">Exame</th>
                <th className="px-3 py-2.5">Alvo</th>
                <th className="px-3 py-2.5">DP</th>
                <th className="px-3 py-2.5">Validade</th>
                <th className="px-3 py-2.5 text-center">Ações</th>
              </tr>
            </thead>
            <tbody>
              {filteredReferences.map((reference) => (
                <tr key={reference.id} className="border-b border-neutral-50 hover:bg-neutral-50/50">
                  <td className="px-3 py-2.5 font-medium text-neutral-900">{reference.name}</td>
                  <td className="px-3 py-2.5 text-neutral-700">{reference.exam.name}</td>
                  <td className="px-3 py-2.5 font-mono text-neutral-700">{reference.targetValue.toFixed(2)}</td>
                  <td className="px-3 py-2.5 font-mono text-neutral-600">{reference.targetSd.toFixed(2)}</td>
                  <td className="whitespace-nowrap px-3 py-2.5 text-neutral-600">
                    {reference.validFrom ? formatDate(reference.validFrom) : '—'}
                    {' → '}
                    {reference.validUntil ? formatDate(reference.validUntil) : 'sem fim'}
                  </td>
                  <td className="px-3 py-2.5 text-center">
                    <div className="flex justify-center gap-1">
                      <button
                        type="button"
                        className="rounded-lg p-1.5 text-neutral-400 transition hover:bg-neutral-100 hover:text-neutral-700"
                        onClick={() => openEdit(reference)}
                        title="Editar"
                      >
                        <Pencil className="h-4 w-4" />
                      </button>
                      <button
                        type="button"
                        className="rounded-lg p-1.5 text-neutral-400 transition hover:bg-red-50 hover:text-red-600"
                        onClick={() => void handleDelete(reference.id)}
                        title="Excluir"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        )}
      </Card>

      <ReferenceModal
        area={area}
        exams={exams}
        registroNameOptions={registroNameOptions}
        form={form}
        editing={editing}
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onSave={handleSave}
        setForm={setForm}
        isSaving={createReference.isPending || updateReference.isPending}
      />
    </div>
  )
}

function formatDate(value: string) {
  try {
    return new Date(value + 'T00:00:00').toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric' })
  } catch {
    return value
  }
}

interface ReferenceModalProps {
  area: string
  exams: QcExam[]
  registroNameOptions: ComboboxOption[]
  form: QcReferenceRequest
  editing: QcReferenceValue | null
  isOpen: boolean
  onClose: () => void
  onSave: () => void
  setForm: Dispatch<SetStateAction<QcReferenceRequest>>
  isSaving: boolean
}

function ReferenceModal({ area, exams, registroNameOptions, form, editing, isOpen, onClose, onSave, setForm, isSaving }: ReferenceModalProps) {
  const { toast } = useToast()
  const createExam = useCreateQcExam()
  const [showNewExam, setShowNewExam] = useState(false)
  const [newExamName, setNewExamName] = useState('')

  const handleCreateExam = async () => {
    if (!newExamName.trim()) {
      toast.warning('Informe o nome do exame.')
      return
    }
    try {
      const exam = await createExam.mutateAsync({ name: newExamName.trim(), area })
      setForm((current) => ({ ...current, examId: exam.id }))
      toast.success(`Exame "${exam.name}" criado.`)
      setNewExamName('')
      setShowNewExam(false)
    } catch {
      toast.error('Não foi possível criar o exame.')
    }
  }

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={editing ? 'Editar Referência' : 'Nova Referência de CQ'}
      footer={
        <div className="flex justify-end gap-3">
          <Button variant="ghost" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={onSave} loading={isSaving}>
            Salvar
          </Button>
        </div>
      }
    >
      <div className="space-y-4">
        {/* Nome do Registro — Combobox com busca + criar novo */}
        <Combobox
          label="Nome do Registro"
          placeholder="Busque ou digite um novo nome"
          value={form.name}
          onChange={(next) => setForm((current) => ({ ...current, name: next }))}
          options={registroNameOptions}
          allowCustom
          createLabel="Criar nome novo"
          emptyText="Nenhum registro cadastrado — digite para criar"
        />

        {/* Nome do Exame */}
        <div className="space-y-2">
          <Select label="Nome do Exame" value={form.examId} onChange={(event) => setForm((current) => ({ ...current, examId: event.target.value }))}>
            <option value="">Selecione o exame</option>
            {exams.map((exam) => (
              <option key={exam.id} value={exam.id}>
                {exam.name}
              </option>
            ))}
          </Select>
          {!showNewExam ? (
            <button
              type="button"
              onClick={() => setShowNewExam(true)}
              className="text-sm font-medium text-green-700 hover:text-green-800"
            >
              + Adicionar exame
            </button>
          ) : (
            <div className="flex items-end gap-2 rounded-xl bg-neutral-50 p-3">
              <Input
                label="Nome do exame"
                value={newExamName}
                onChange={(event) => setNewExamName(event.target.value)}
                placeholder="Ex: Glicose, Colesterol..."
              />
              <Button size="sm" onClick={handleCreateExam} loading={createExam.isPending}>
                Criar
              </Button>
              <Button size="sm" variant="ghost" onClick={() => { setShowNewExam(false); setNewExamName('') }}>
                Cancelar
              </Button>
            </div>
          )}
        </div>

        {/* Preencher com última referência */}
        {form.examId && !editing && (
          <button
            type="button"
            className="text-sm text-green-700 hover:text-green-800 underline"
            onClick={async () => {
              try {
                const last = await qcService.getLastReference(form.examId, form.level)
                setForm(c => ({
                  ...c,
                  targetValue: last.targetValue ?? c.targetValue,
                  targetSd: last.targetSd ?? c.targetSd,
                }))
              } catch { /* nenhuma referencia anterior */ }
            }}
          >
            Preencher com última referência
          </button>
        )}

        {/* Validades + Valores */}
        <div className="grid gap-4 md:grid-cols-2">
          <Input
            label="Válido a partir de"
            type="date"
            value={form.validFrom}
            onChange={(event) => setForm((current) => ({ ...current, validFrom: event.target.value }))}
          />
          <Input
            label="Válido até (opcional)"
            type="date"
            value={form.validUntil}
            onChange={(event) => setForm((current) => ({ ...current, validUntil: event.target.value }))}
          />
        </div>

        <div className="grid gap-4 md:grid-cols-2">
          <Input
            label="Valor Alvo (Média de Controle)"
            type="number"
            step="0.01"
            value={String(form.targetValue)}
            onChange={(event) => setForm((current) => ({ ...current, targetValue: Number(event.target.value) }))}
          />
          <Input
            label="Desvio Padrão"
            type="number"
            step="0.01"
            value={String(form.targetSd)}
            onChange={(event) => setForm((current) => ({ ...current, targetSd: Number(event.target.value) }))}
          />
        </div>
      </div>
    </Modal>
  )
}
