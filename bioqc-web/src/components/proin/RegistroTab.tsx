import axios from 'axios'
import { Activity, AlertTriangle, CheckCircle2, ChevronLeft, ChevronRight, CircleX, Search, Trash2, X, XCircle } from 'lucide-react'
import { lazy, Suspense, useMemo, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useCreateQcRecord, useQcExams, useQcRecords, useQcReferences } from '../../hooks/useQcRecords'
import { qcService } from '../../services/qcService'
import type { QcRecord, QcRecordRequest, QcReferenceValue } from '../../types'
import { Button, Card, Input, Modal, Select, Skeleton, StatusBadge, useToast } from '../ui'
import { PostCalibrationModal } from './PostCalibrationModal'
import { VoiceRecorderModal } from './VoiceRecorderModal'
import { ExamHistoryModal } from './ExamHistoryModal'

const LeveyJenningsChart = lazy(() =>
  import('../charts/LeveyJenningsChart').then((module) => ({ default: module.LeveyJenningsChart })),
)

interface RegistroTabProps {
  area: string
}

interface WestgardInfo {
  title: string
  detail: string
  action: string
}

const WESTGARD_INFO: Record<string, WestgardInfo> = {
  '1-2s': {
    title: 'Valor fora da faixa de alerta (±2 DP)',
    detail: 'Apenas um controle excedeu 2 desvios padrão.',
    action: 'Alerta preventivo: pode liberar o resultado, mas observe a próxima corrida.',
  },
  '1-3s': {
    title: 'Valor fora da faixa crítica (±3 DP)',
    detail: 'Erro aleatório: o valor está muito distante do alvo.',
    action: 'Não libere resultados. Recalibre e refaça o CQ antes de continuar.',
  },
  '2-2s': {
    title: 'Dois controles seguidos fora de ±2 DP',
    detail: 'Erro sistemático: padrão de desvio persistente.',
    action: 'Verifique calibração/reagente. Refaça o CQ antes de liberar resultados.',
  },
  'R-4s': {
    title: 'Diferença entre controles maior que 4 DP',
    detail: 'Erro aleatório grave entre corridas consecutivas.',
    action: 'Não libere resultados. Recalibre e refaça o CQ.',
  },
  '4-1s': {
    title: 'Quatro controles seguidos fora de ±1 DP',
    detail: 'Tendência persistente — possível erro sistemático leve.',
    action: 'Inspecione reagente, calibração e condições antes do próximo lote.',
  },
  '10x': {
    title: 'Dez controles seguidos do mesmo lado da média',
    detail: 'Viés sistemático detectado.',
    action: 'Revise calibração, reagente e armazenamento. Refaça o CQ.',
  },
  'SD=0': {
    title: 'Desvio padrão zerado na referência',
    detail: 'A referência está sem DP, o cálculo Westgard não é confiável.',
    action: 'Ajuste o DP na aba Referências antes de registrar novos valores.',
  },
}

function getWestgardInfo(rule: string): WestgardInfo {
  return WESTGARD_INFO[rule] ?? {
    title: rule,
    detail: 'Regra Westgard acionada.',
    action: 'Verifique o controle antes de liberar resultados.',
  }
}

const today = () => new Date().toISOString().slice(0, 10)

function calcCv(value: number, target: number) {
  if (!target) return 0
  return (Math.abs(value - target) / Math.abs(target)) * 100
}

export function RegistroTab({ area }: RegistroTabProps) {
  const { toast } = useToast()
  const queryClient = useQueryClient()
  const createRecord = useCreateQcRecord()
  const { data: exams = [] } = useQcExams(area)
  const { data: references = [] } = useQcReferences(undefined, true)

  // --- Undo delete ---
  const [deletedRecord, setDeletedRecord] = useState<{ request: QcRecordRequest; timeout: number } | null>(null)

  // --- Batch mode ---
  const [batchMode, setBatchMode] = useState(false)
  const [batchRows, setBatchRows] = useState<Array<{
    examName: string; value: string; targetValue: string; targetSd: string; cvLimit: string
  }>>([{ examName: '', value: '', targetValue: '', targetSd: '', cvLimit: '10' }])

  // --- Estado do formulario ---
  const emptyForm: QcRecordRequest = {
    examName: '', area, date: today(), level: 'Normal',
    lotNumber: '', value: 0, targetValue: 0, targetSd: 0,
    cvLimit: 10, equipment: '', analyst: '',
  }
  const [form, setForm] = useState<QcRecordRequest>(emptyForm)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [feedback, setFeedback] = useState<QcRecord | null>(null)

  // --- Pos-calibracao ---
  const [postCalRecord, setPostCalRecord] = useState<QcRecord | null>(null)
  const [isPostCalOpen, setIsPostCalOpen] = useState(false)
  const [lastCreated, setLastCreated] = useState<QcRecord | null>(null)

  // --- Historico ---
  const [historyDate, setHistoryDate] = useState(today())
  const [searchTerm, setSearchTerm] = useState('')
  const [statusFilter, setStatusFilter] = useState('Todos')
  const { data: allRecords = [], isLoading } = useQcRecords({ area })
  const [chartRecord, setChartRecord] = useState<{ examName: string; level: string } | null>(null)
  const [historyExam, setHistoryExam] = useState<{ examName: string; level: string | null } | null>(null)

  // --- Resolucao de referencia silenciosa ---
  const resolvedRef = useMemo<QcReferenceValue | null>(() => {
    if (!form.examName) return null
    const matching = references.filter((r) =>
      r.isActive && r.exam?.area === area && r.exam.name === form.examName &&
      r.level === 'Normal' && isRefValidOnDate(r, form.date),
    )
    if (matching.length === 1) return matching[0]
    if (form.referenceId) return matching.find((r) => r.id === form.referenceId) ?? null
    return null
  }, [area, form.examName, form.date, form.referenceId, references])

  const ambiguousRefs = useMemo(() => {
    if (!form.examName || resolvedRef) return []
    return references.filter((r) =>
      r.isActive && r.exam?.area === area && r.exam.name === form.examName &&
      r.level === 'Normal' && isRefValidOnDate(r, form.date),
    )
  }, [area, form.examName, form.date, resolvedRef, references])

  const targetValue = resolvedRef ? resolvedRef.targetValue : form.targetValue || 0
  const targetSd = resolvedRef ? resolvedRef.targetSd : form.targetSd || 0
  const cvLimit = form.cvLimit || resolvedRef?.cvMaxThreshold || 10

  // CV% em tempo real
  const liveCv = calcCv(Number(form.value), targetValue)
  const cvOk = liveCv <= cvLimit

  // Filtro de historico por dia
  const filteredRecords = useMemo(() => {
    return allRecords
      .filter((r) => r.date === historyDate)
      .filter((r) => !searchTerm || r.examName.toLowerCase().includes(searchTerm.toLowerCase()))
      .filter((r) => {
        if (statusFilter === 'Todos') return true
        if (statusFilter === 'OK') return r.status === 'APROVADO'
        if (statusFilter === 'ALERTA') return r.status === 'ALERTA'
        if (statusFilter === 'ERRO') return r.status === 'REPROVADO'
        return true
      })
  }, [allRecords, historyDate, searchTerm, statusFilter])

  const clearMessages = () => { setFeedback(null); setSubmitError(null) }

  const clearForm = () => {
    setForm(emptyForm)
    clearMessages()
  }

  const handleSubmit = async () => {
    clearMessages()
    if (!form.examName || !form.value) {
      toast.warning('Selecione um exame e informe o valor.')
      return
    }
    if (!resolvedRef && ambiguousRefs.length === 0) {
      setSubmitError('Cadastre uma referência para este exame antes de registrar.')
      return
    }
    if (!resolvedRef && ambiguousRefs.length > 1) {
      setSubmitError('Selecione a referência correta abaixo.')
      return
    }
    const ref = resolvedRef!
    const payload: QcRecordRequest = {
      ...form, area, referenceId: ref.id,
      lotNumber: form.lotNumber || ref.lotNumber || '',
      value: Number(form.value), targetValue: targetValue,
      targetSd: targetSd, cvLimit: cvLimit,
    }

    try {
      const response = await createRecord.mutateAsync(payload)
      setLastCreated(response)
      setFeedback(response)
      if (response.referenceWarning) {
        toast.warning(response.referenceWarning)
      }
      setForm({ ...emptyForm, equipment: form.equipment, analyst: form.analyst })
    } catch (error) {
      const msg = axios.isAxiosError(error) && error.response?.data?.message
        ? error.response.data.message : 'Erro ao salvar registro.'
      setSubmitError(msg)
      toast.error(msg)
    }
  }

  const shiftDay = (delta: number) => {
    const d = new Date(historyDate + 'T00:00:00')
    d.setDate(d.getDate() + delta)
    setHistoryDate(d.toISOString().slice(0, 10))
  }

  const loadRecords = () => {
    void queryClient.invalidateQueries({ queryKey: ['qc-records'] })
    void queryClient.invalidateQueries({ queryKey: ['dashboard'] })
  }

  // --- Undo delete handlers ---
  const handleDeleteRecord = async (record: QcRecord) => {
    try {
      await qcService.deleteRecord(record.id)
      const undoRequest: QcRecordRequest = {
        examName: record.examName, area: record.area, date: record.date,
        level: record.level, lotNumber: record.lotNumber ?? '',
        value: record.value, targetValue: record.targetValue,
        targetSd: record.targetSd, cvLimit: record.cvLimit,
        equipment: record.equipment ?? '', analyst: record.analyst ?? '',
        referenceId: record.referenceId ?? undefined,
      }
      if (deletedRecord?.timeout) window.clearTimeout(deletedRecord.timeout)
      const timeout = window.setTimeout(() => setDeletedRecord(null), 5000)
      setDeletedRecord({ request: undoRequest, timeout })
      loadRecords()
    } catch { toast.error('Erro ao excluir registro.') }
  }

  const handleUndo = async () => {
    if (!deletedRecord) return
    window.clearTimeout(deletedRecord.timeout)
    try {
      await qcService.createRecord(deletedRecord.request)
      setDeletedRecord(null)
      loadRecords()
      toast.success('Registro restaurado.')
    } catch { toast.error('Erro ao restaurar registro.') }
  }

  // --- Batch submit handler ---
  const handleBatchSubmit = async () => {
    const validRows = batchRows.filter(r => r.examName && r.value)
    if (validRows.length === 0) return
    const requests: QcRecordRequest[] = validRows.map(row => ({
      examName: row.examName,
      area,
      date: form.date || new Date().toISOString().slice(0, 10),
      level: 'Normal',
      lotNumber: '',
      value: parseFloat(row.value),
      targetValue: parseFloat(row.targetValue) || 0,
      targetSd: parseFloat(row.targetSd) || 0,
      cvLimit: parseFloat(row.cvLimit) || 10,
      equipment: form.equipment || '',
      analyst: form.analyst || '',
    }))
    try {
      await qcService.createBatch(requests)
      toast.success(`${validRows.length} registros criados com sucesso!`)
      setBatchRows([{ examName: '', value: '', targetValue: '', targetSd: '', cvLimit: '10' }])
      loadRecords()
    } catch (err: unknown) {
      const msg = axios.isAxiosError(err) && err.response?.data?.message
        ? err.response.data.message : 'Erro ao criar registros em lote.'
      toast.error(msg)
    }
  }

  return (
    <div className="space-y-6">
      <Card>
        <div className="mb-4 flex items-center justify-between">
          <div>
            <h3 className="text-xl font-semibold text-neutral-900">Registro de CQ</h3>
            <p className="text-base text-neutral-500">Insira os dados diários para cálculo automático da Variação %</p>
          </div>
          <div className="flex items-center gap-2">
            <button onClick={() => setBatchMode(!batchMode)}
              className={`rounded-full px-3 py-1 text-sm ${batchMode ? 'bg-green-700 text-white' : 'bg-neutral-100 text-neutral-600 hover:bg-neutral-200'}`}>
              {batchMode ? 'Modo Normal' : 'Modo Planilha'}
            </button>
            <VoiceRecorderModal
              formType="registro"
              title="Preencher por voz"
              onApply={(data) => {
                setForm((c) => ({
                  ...c,
                  examName: typeof data.exam_name === 'string' ? data.exam_name : c.examName,
                  value: typeof data.value === 'number' ? data.value : c.value,
                  targetValue: typeof data.target_value === 'number' ? data.target_value : c.targetValue,
                  equipment: typeof data.equipment === 'string' ? data.equipment : c.equipment,
                  analyst: typeof data.analyst === 'string' ? data.analyst : c.analyst,
                }))
              }}
            />
          </div>
        </div>

        {batchMode ? (
          /* --- Batch / Planilha Mode --- */
          <div className="space-y-3">
            {/* Shared fields: date, equipment, analyst */}
            <div className="grid gap-3 sm:grid-cols-3">
              <Input label="Data" type="date" value={form.date} onChange={(e) => setForm((c) => ({ ...c, date: e.target.value }))} />
              <Input label="Equipamento" placeholder="Ex: Cobas c111" value={form.equipment} onChange={(e) => setForm((c) => ({ ...c, equipment: e.target.value }))} />
              <Input label="Analista" placeholder="Nome do analista" value={form.analyst} onChange={(e) => setForm((c) => ({ ...c, analyst: e.target.value }))} />
            </div>

            {/* Batch grid header */}
            <div className="grid grid-cols-[1fr_100px_100px_100px_100px_40px] gap-2 text-xs font-medium text-neutral-500 px-1">
              <span>Exame</span><span>Valor</span><span>Alvo</span><span>DP</span><span>CV Lim</span><span></span>
            </div>

            {/* Batch rows */}
            {batchRows.map((row, i) => (
              <div key={i} className="grid grid-cols-[1fr_100px_100px_100px_100px_40px] gap-2 items-center">
                <select value={row.examName} onChange={e => {
                  const val = e.target.value
                  const ref = references.find(r =>
                    r.exam?.name?.toLowerCase() === val.toLowerCase() &&
                    r.exam?.area?.toLowerCase() === area.toLowerCase() &&
                    r.isActive,
                  )
                  if (ref) {
                    setBatchRows(prev => prev.map((r, idx) => idx === i ? {
                      ...r, examName: val,
                      targetValue: String(ref.targetValue ?? ''),
                      targetSd: String(ref.targetSd ?? ''),
                      cvLimit: String(ref.cvMaxThreshold ?? '10'),
                    } : r))
                  } else {
                    setBatchRows(prev => prev.map((r, idx) => idx === i ? { ...r, examName: val } : r))
                  }
                }} className="rounded-xl border border-neutral-200 bg-white px-3 py-2 text-sm">
                  <option value="">Selecione...</option>
                  {exams.map(e => <option key={e.id} value={e.name}>{e.name}</option>)}
                </select>
                <input type="number" step="0.01" value={row.value}
                  onChange={e => setBatchRows(prev => prev.map((r, idx) => idx === i ? { ...r, value: e.target.value } : r))}
                  className="rounded-xl border border-neutral-200 bg-white px-3 py-2 text-sm" placeholder="0.00" />
                <input type="number" step="0.01" value={row.targetValue} disabled
                  className="rounded-xl border border-neutral-100 bg-neutral-50 px-3 py-2 text-sm text-neutral-500" />
                <input type="number" step="0.01" value={row.targetSd} disabled
                  className="rounded-xl border border-neutral-100 bg-neutral-50 px-3 py-2 text-sm text-neutral-500" />
                <input type="number" step="0.01" value={row.cvLimit} disabled
                  className="rounded-xl border border-neutral-100 bg-neutral-50 px-3 py-2 text-sm text-neutral-500" />
                <button onClick={() => setBatchRows(prev => prev.length > 1 ? prev.filter((_, idx) => idx !== i) : prev)}
                  className="text-red-400 hover:text-red-600 text-lg" title="Remover linha">&times;</button>
              </div>
            ))}

            <div className="flex gap-2">
              <button onClick={() => setBatchRows(prev => [...prev, { examName: '', value: '', targetValue: '', targetSd: '', cvLimit: '10' }])}
                className="text-sm text-green-700 hover:text-green-800 underline">
                + Adicionar linha
              </button>
            </div>

            <Button onClick={handleBatchSubmit} disabled={batchRows.every(r => !r.examName || !r.value)}>
              Registrar Todos ({batchRows.filter(r => r.examName && r.value).length})
            </Button>
          </div>
        ) : (
          /* --- Normal Mode --- */
          <>
            {/* Linha 1: Exame, Data, Medição, Valor Alvo */}
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <Select label="Exame" value={form.examName} onChange={(e) => setForm((c) => ({ ...c, examName: e.target.value, referenceId: undefined, targetValue: 0, targetSd: 0 }))}>
                <option value="">Selecione o exame</option>
                {exams.map((ex) => <option key={ex.id} value={ex.name}>{ex.name}</option>)}
              </Select>
              <Input label="Data" type="date" value={form.date} onChange={(e) => setForm((c) => ({ ...c, date: e.target.value }))} />
              <Input label="Medição" type="number" step="0.01" placeholder="0.00" value={String(form.value)} onChange={(e) => setForm((c) => ({ ...c, value: Number(e.target.value) }))} />
              <Input label="Valor Alvo" type="number" step="0.01" placeholder="0.00" value={String(targetValue)} onChange={(e) => setForm((c) => ({ ...c, targetValue: Number(e.target.value) }))} disabled={Boolean(resolvedRef)} />
            </div>

            {/* Linha 2: CV Limite, CV% (tempo real), Equipamento, Analista */}
            <div className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <Input label="CV Limite (%)" type="number" step="0.01" placeholder="10" value={String(cvLimit)} onChange={(e) => setForm((c) => ({ ...c, cvLimit: Number(e.target.value) }))} />
              <div className="space-y-1">
                <span className="text-base font-medium text-neutral-700">Variação %</span>
                <div className={`flex items-center gap-2 rounded-xl border px-4 py-3 ${cvOk ? 'border-green-300 bg-green-50' : 'border-red-300 bg-red-50'}`}>
                  <span className={`text-lg font-bold ${cvOk ? 'text-green-700' : 'text-red-700'}`}>
                    {liveCv.toFixed(2)}%
                  </span>
                  {cvOk ? <CheckCircle2 className="h-5 w-5 text-green-600" /> : <XCircle className="h-5 w-5 text-red-600" />}
                </div>
              </div>
              <Input label="Equipamento" placeholder="Ex: Cobas c111" value={form.equipment} onChange={(e) => setForm((c) => ({ ...c, equipment: e.target.value }))} />
              <Input label="Analista" placeholder="Nome do analista" value={form.analyst} onChange={(e) => setForm((c) => ({ ...c, analyst: e.target.value }))} />
            </div>

            {/* Indicador de referência compacto */}
            {resolvedRef ? (
              <div className="mt-3 flex flex-wrap items-center gap-1 rounded-xl border border-green-200 bg-green-50 px-3 py-2">
                <CheckCircle2 className="h-4 w-4 text-green-600" />
                <span className="text-sm font-semibold text-neutral-600">Ref:</span>
                <span className="text-sm">{resolvedRef.name}</span>
                <span className="text-sm text-neutral-500">| Alvo: {resolvedRef.targetValue} | DP: {resolvedRef.targetSd}</span>
              </div>
            ) : form.examName && ambiguousRefs.length > 1 ? (
              <div className="mt-3 space-y-2">
                <div className="flex items-center gap-1 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                  Mais de uma referência encontrada. Selecione:
                </div>
                <Select value={form.referenceId ?? ''} onChange={(e) => setForm((c) => ({ ...c, referenceId: e.target.value || undefined }))}>
                  <option value="">Selecione a referência</option>
                  {ambiguousRefs.map((r) => <option key={r.id} value={r.id}>{r.name} — Alvo: {r.targetValue}, DP: {r.targetSd}</option>)}
                </Select>
              </div>
            ) : form.examName ? (
              <div className="mt-3 flex items-center gap-1 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                <CircleX className="h-4 w-4" /> Sem referência ativa. Cadastre uma referência para este exame.
              </div>
            ) : null}

            {/* Botões */}
            <div className="mt-4 flex justify-end gap-3">
              <Button variant="secondary" onClick={clearForm}>Limpar</Button>
              <Button onClick={handleSubmit} loading={createRecord.isPending}>Salvar Registro</Button>
            </div>

            {/* Feedback estruturado */}
            {feedback ? (
              <FeedbackPanel
                record={feedback}
                onDismiss={() => setFeedback(null)}
                onOpenPostCal={() => setIsPostCalOpen(true)}
              />
            ) : null}
            {submitError ? (
              <div className="mt-3 flex items-start gap-2 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-base text-red-800">
                <XCircle className="mt-0.5 h-5 w-5 flex-none" />
                <span>{submitError}</span>
              </div>
            ) : null}
          </>
        )}
      </Card>

      {/* Modal pos-calibracao */}
      <PostCalibrationModal
        key={postCalRecord ? `h-${postCalRecord.id}` : lastCreated ? `c-${lastCreated.id}` : 'none'}
        record={postCalRecord ?? lastCreated}
        isOpen={postCalRecord !== null || isPostCalOpen}
        onClose={() => { setPostCalRecord(null); setIsPostCalOpen(false) }}
        onSaved={() => {
          setPostCalRecord(null); setIsPostCalOpen(false)
          toast.success('Pós-calibração registrada.')
        }}
      />

      {/* Historico */}
      <Card>
        <div className="flex items-center justify-between">
          <h3 className="text-xl font-semibold text-neutral-900">Histórico</h3>
        </div>

        {/* Busca e filtro */}
        <div className="mt-3 flex flex-wrap items-center gap-3">
          <div className="relative max-w-[280px] flex-1">
            <Input placeholder="Buscar exame..." value={searchTerm} onChange={(e) => setSearchTerm(e.target.value)} />
            <Search className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-neutral-400" />
          </div>
          <Select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} className="w-[130px]">
            <option>Todos</option>
            <option>OK</option>
            <option>ALERTA</option>
            <option>ERRO</option>
          </Select>
          <span className="text-sm text-neutral-500">{filteredRecords.length} registros no dia</span>
        </div>

        {/* Tabela */}
        {isLoading ? (
          <div className="mt-4 space-y-2">{Array.from({ length: 4 }).map((_, i) => <div key={i} className="h-12 animate-pulse rounded-xl bg-neutral-100" />)}</div>
        ) : filteredRecords.length === 0 ? (
          <div className="mt-4 rounded-xl border border-neutral-200 bg-neutral-50 px-4 py-8 text-center text-base text-neutral-500">Nenhum registro encontrado.</div>
        ) : (
          <div className="mt-4 overflow-x-auto">
            <table className="w-full text-left">
              <thead>
                <tr className="border-b border-neutral-100 text-xs uppercase tracking-wider text-neutral-500">
                  <th className="px-3 py-2.5">Data</th>
                  <th className="px-3 py-2.5">Exame</th>
                  <th className="px-3 py-2.5">Valor</th>
                  <th className="px-3 py-2.5">CV%</th>
                  <th className="px-3 py-2.5">CV Lim%</th>
                  <th className="px-3 py-2.5">Status</th>
                  <th className="px-3 py-2.5">Calibrar?</th>
                  <th className="px-3 py-2.5">Pós-Calib</th>
                  <th className="px-3 py-2.5">LJ</th>
                  <th className="px-3 py-2.5 text-right">Ação</th>
                </tr>
              </thead>
              <tbody>
                {filteredRecords.map((r) => {
                  const rCv = r.cv ?? 0
                  const rCvLimit = r.cvLimit ?? 10
                  const needsCal = rCv > rCvLimit
                  return (
                    <tr key={r.id} className="border-b border-neutral-50 hover:bg-neutral-50/50">
                      <td className="whitespace-nowrap px-3 py-2.5 text-base text-neutral-600">{formatDate(r.date)}</td>
                      <td className="px-3 py-2.5 text-base font-semibold">
                        <button
                          type="button"
                          onClick={() => setHistoryExam({ examName: r.examName, level: r.level })}
                          className="text-left text-green-900 underline-offset-2 hover:underline focus:outline-none focus:underline"
                          title={`Ver histórico completo de ${r.examName}`}
                        >
                          {r.examName}
                        </button>
                      </td>
                      <td className="px-3 py-2.5 font-mono text-base">{r.value.toFixed(2)}</td>
                      <td className="px-3 py-2.5">
                        <span className={`font-semibold ${rCv <= rCvLimit ? 'text-green-700' : 'text-red-700'}`}>{rCv.toFixed(2)}%</span>
                      </td>
                      <td className="px-3 py-2.5">
                        <span className="font-mono text-neutral-700">{rCvLimit.toFixed(2)}%</span>
                      </td>
                      <td className="px-3 py-2.5"><StatusBadge status={r.status} /></td>
                      <td className="px-3 py-2.5">
                        {needsCal ? (
                          r.needsCalibration ? (
                            <button onClick={() => setPostCalRecord(r)} className="rounded-full bg-red-100 px-3 py-1 text-sm font-semibold text-red-800 transition hover:bg-red-200" title="Clique para registrar pós-calibração">
                              SIM
                            </button>
                          ) : (
                            <span className="rounded-full bg-blue-100 px-3 py-1 text-sm font-semibold text-blue-800" title="Pós-calibração já registrada">FEITO</span>
                          )
                        ) : (
                          <span className="rounded-full border border-green-300 px-3 py-1 text-sm font-semibold text-green-700">NÃO</span>
                        )}
                      </td>
                      <td className="px-3 py-2.5">
                        {r.postCalibrationStatus ? (
                          <div className="flex flex-col gap-0.5">
                            <span
                              className={`inline-flex w-fit rounded-full px-2 py-0.5 text-xs font-semibold ${
                                r.postCalibrationStatus === 'APROVADO'
                                  ? 'bg-green-100 text-green-800'
                                  : 'bg-red-100 text-red-800'
                              }`}
                            >
                              {r.postCalibrationStatus}
                            </span>
                            <span className="font-mono text-xs text-neutral-500">
                              {r.postCalibrationValue?.toFixed(2)} ({r.postCalibrationCv?.toFixed(2)}%)
                            </span>
                          </div>
                        ) : (
                          <span className="text-neutral-400">—</span>
                        )}
                      </td>
                      <td className="px-3 py-2.5">
                        <button onClick={() => setChartRecord({ examName: r.examName, level: r.level })} className="rounded-lg p-1.5 text-green-700 hover:bg-green-50" title="Levey-Jennings">
                          <Activity className="h-5 w-5" />
                        </button>
                      </td>
                      <td className="px-3 py-2.5 text-right">
                        <button onClick={() => handleDeleteRecord(r)}
                          className="text-red-400 hover:text-red-600 transition-colors" title="Excluir">
                          <Trash2 size={16} />
                        </button>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}

        {/* Navegação por dia */}
        <div className="mt-4 flex items-center justify-center gap-3">
          <button onClick={() => shiftDay(-1)} className="rounded-lg border border-neutral-200 p-2 hover:bg-neutral-50" title="Dia anterior"><ChevronLeft className="h-5 w-5" /></button>
          <input type="date" value={historyDate} onChange={(e) => setHistoryDate(e.target.value)} className="rounded-lg border border-neutral-200 px-3 py-2 text-base" />
          <button onClick={() => shiftDay(1)} className="rounded-lg border border-neutral-200 p-2 hover:bg-neutral-50" title="Próximo dia"><ChevronRight className="h-5 w-5" /></button>
        </div>
      </Card>

      {/* Modal Levey-Jennings */}
      <Modal isOpen={chartRecord !== null} onClose={() => setChartRecord(null)} title={chartRecord ? `Levey-Jennings — ${chartRecord.examName}` : ''} size="lg">
        {chartRecord ? (
          <Suspense fallback={<Skeleton height="24rem" />}>
            <LeveyJenningsChart examName={chartRecord.examName} level={chartRecord.level} area={area} />
          </Suspense>
        ) : null}
      </Modal>

      {/* Modal Historico do Exame (clique no nome do exame) */}
      <ExamHistoryModal
        area={area}
        examName={historyExam?.examName ?? null}
        level={historyExam?.level ?? null}
        onClose={() => setHistoryExam(null)}
      />

      {/* Undo delete banner */}
      {deletedRecord && (
        <div className="fixed bottom-4 right-4 z-50 flex items-center gap-3 rounded-2xl border border-amber-200 bg-white px-4 py-3 shadow-lg animate-slideUp">
          <span className="text-base text-neutral-700">Registro excluído.</span>
          <button onClick={handleUndo} className="font-semibold text-green-700 hover:text-green-800">
            Desfazer
          </button>
        </div>
      )}
    </div>
  )
}

interface FeedbackPanelProps {
  record: QcRecord
  onDismiss: () => void
  onOpenPostCal: () => void
}

function FeedbackPanel({ record, onDismiss, onOpenPostCal }: FeedbackPanelProps) {
  const status = record.status
  const tone =
    status === 'APROVADO'
      ? { border: 'border-green-200', bg: 'bg-green-50', text: 'text-green-900', iconColor: 'text-green-600', title: 'Registro aprovado' }
      : status === 'ALERTA'
        ? { border: 'border-amber-200', bg: 'bg-amber-50', text: 'text-amber-900', iconColor: 'text-amber-600', title: 'Atenção necessária' }
        : { border: 'border-red-200', bg: 'bg-red-50', text: 'text-red-900', iconColor: 'text-red-600', title: 'Registro reprovado' }

  const Icon = status === 'APROVADO' ? CheckCircle2 : status === 'ALERTA' ? AlertTriangle : XCircle

  const target = record.targetValue ?? 0
  const sd = record.targetSd ?? 0
  const context = `${record.examName} · ${record.value.toFixed(2)} · Alvo ${target.toFixed(2)} ± ${sd.toFixed(2)} · Z ${record.zScore.toFixed(2)}`

  return (
    <div className={`mt-3 rounded-2xl border ${tone.border} ${tone.bg} px-5 py-4 ${tone.text}`}>
      <div className="flex items-start gap-3">
        <Icon className={`mt-0.5 h-7 w-7 flex-none ${tone.iconColor}`} />
        <div className="flex-1">
          <div className="flex items-start justify-between gap-3">
            <div>
              <div className="text-lg font-semibold">{tone.title}</div>
              <div className="text-sm opacity-80">{context}</div>
            </div>
            <button
              type="button"
              onClick={onDismiss}
              className="rounded-lg p-1 text-current opacity-60 transition hover:bg-black/5 hover:opacity-100"
              aria-label="Dispensar"
            >
              <X className="h-4 w-4" />
            </button>
          </div>

          {record.violations.length > 0 ? (
            <ul className="mt-3 space-y-2">
              {record.violations.map((v, idx) => {
                const info = getWestgardInfo(v.rule)
                return (
                  <li key={idx} className="rounded-xl bg-white/70 px-3 py-2">
                    <div className="flex items-center gap-2">
                      <span className="font-semibold">{info.title}</span>
                      <span className="rounded-full bg-black/10 px-2 py-0.5 font-mono text-xs">{v.rule}</span>
                    </div>
                    <div className="text-sm opacity-80">{info.detail}</div>
                    <div className="mt-1 text-sm font-medium">→ {info.action}</div>
                  </li>
                )
              })}
            </ul>
          ) : status === 'APROVADO' ? (
            <div className="mt-2 text-sm opacity-80">Valor dentro da faixa aceitável. Pode liberar os resultados.</div>
          ) : null}

          {record.needsCalibration ? (
            <div className="mt-3">
              <button
                type="button"
                onClick={onOpenPostCal}
                className="inline-flex items-center gap-2 rounded-xl bg-red-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-red-700"
              >
                Registrar pós-calibração agora
              </button>
            </div>
          ) : null}
        </div>
      </div>
    </div>
  )
}

function isRefValidOnDate(ref: QcReferenceValue, date: string) {
  const d = date || new Date().toISOString().slice(0, 10)
  const from = ref.validFrom?.slice(0, 10)
  const until = ref.validUntil?.slice(0, 10)
  return (!from || from <= d) && (!until || until >= d)
}

function formatDate(date: string) {
  try { return new Date(date + 'T00:00:00').toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric' }) }
  catch { return date }
}
