import axios from 'axios'
import { Download, FlaskConical, Microscope, Trash2 } from 'lucide-react'
import { useMemo, useState } from 'react'
import {
  useAreaQcMeasurements,
  useAreaQcParameters,
  useCreateAreaQcMeasurement,
  useCreateAreaQcParameter,
  useDeleteAreaQcParameter,
} from '../../hooks/useAreaQc'
import { reportService } from '../../services/reportService'
import type { AreaQcMeasurementRequest, AreaQcParameter, AreaQcParameterRequest, LabArea } from '../../types'
import { Button, Card, EmptyState, Input, Select, StatusBadge, TextArea, useToast } from '../ui'
import { formatLongBR } from '../../utils/date'

interface AreaQcModuleProps {
  area: Exclude<LabArea, 'bioquimica' | 'hematologia'>
  title: string
  description: string
  analytes: string[]
}

const emptyParameterForm: AreaQcParameterRequest = {
  analito: '',
  equipamento: '',
  loteControle: '',
  nivelControle: '',
  modo: 'INTERVALO',
  alvoValor: 0,
  minValor: 0,
  maxValor: 0,
  toleranciaPercentual: 0,
}

const createMeasurementForm = (): AreaQcMeasurementRequest => ({
  dataMedicao: new Date().toISOString().slice(0, 10),
  analito: '',
  valorMedido: 0,
  parameterId: '',
  equipamento: '',
  loteControle: '',
  nivelControle: '',
  observacao: '',
})

export function AreaQcModule({ area, title, description, analytes }: AreaQcModuleProps) {
  const { toast } = useToast()
  const [parameterForm, setParameterForm] = useState<AreaQcParameterRequest>(emptyParameterForm)
  const [measurementForm, setMeasurementForm] = useState<AreaQcMeasurementRequest>(createMeasurementForm())
  const [measurementError, setMeasurementError] = useState<string | null>(null)
  const [analitoFilter, setAnalitoFilter] = useState('')
  const { data: parameters = [] } = useAreaQcParameters(area)
  const { data: measurements = [] } = useAreaQcMeasurements(area, {
    analito: analitoFilter || undefined,
  })
  const createParameter = useCreateAreaQcParameter(area)
  const createMeasurement = useCreateAreaQcMeasurement(area)
  const deleteParameter = useDeleteAreaQcParameter(area)

  const availableAnalitos = useMemo(
    () => Array.from(new Set([...analytes, ...parameters.map((item) => item.analito)])).sort((left, right) => left.localeCompare(right)),
    [analytes, parameters],
  )

  const measurementCandidates = useMemo(
    () => parameters.filter((item) => item.analito === measurementForm.analito),
    [measurementForm.analito, parameters],
  )

  const selectedParameter = useMemo(
    () => measurementCandidates.find((item) => item.id === measurementForm.parameterId) ?? null,
    [measurementCandidates, measurementForm.parameterId],
  )

  const saveParameter = async () => {
    if (!parameterForm.analito || !parameterForm.alvoValor) {
      toast.warning('Preencha ao menos analito e valor alvo.')
      return
    }

    try {
      await createParameter.mutateAsync(parameterForm)
      toast.success('Parâmetro cadastrado.')
      setParameterForm(emptyParameterForm)
    } catch {
      toast.error('Não foi possível cadastrar o parâmetro.')
    }
  }

  const saveMeasurement = async () => {
    setMeasurementError(null)
    if (!measurementForm.analito || !measurementForm.valorMedido) {
      toast.warning('Preencha analito e valor medido.')
      return
    }

    try {
      const response = await createMeasurement.mutateAsync({
        ...measurementForm,
        parameterId: measurementForm.parameterId || undefined,
      })
      toast[response.status === 'APROVADO' ? 'success' : 'warning'](`Medição registrada: ${response.status}.`)
      setMeasurementForm(createMeasurementForm())
    } catch (error) {
      const message = getAreaQcApiErrorMessage(error, 'Não foi possível registrar a medição.')
      setMeasurementError(message)
      toast.error(message)
    }
  }

  const removeParameter = async (id: string) => {
    try {
      await deleteParameter.mutateAsync(id)
      toast.success('Parâmetro removido.')
    } catch {
      toast.error('Não foi possível remover o parâmetro.')
    }
  }

  const downloadPdf = async () => {
    try {
      const blob = await reportService.getQcPdf({
        area,
        periodType: 'current-month',
        month: String(new Date().getMonth() + 1),
        year: String(new Date().getFullYear()),
      })
      downloadBlob(blob, `${area}-qc-report.pdf`)
    } catch {
      toast.error('Não foi possível gerar o PDF desta área.')
    }
  }

  return (
    <div className="space-y-6">
      <Card className="bg-gradient-to-r from-white to-green-50">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div className="space-y-2">
            <div className="inline-flex items-center gap-2 rounded-full bg-green-100 px-3 py-1 text-xs font-semibold uppercase tracking-[0.16em] text-green-800">
              <Microscope className="h-3.5 w-3.5" />
              Área Especializada
            </div>
            <h2 className="text-2xl font-semibold text-neutral-900">{title}</h2>
            <p className="max-w-3xl text-sm text-neutral-600">{description}</p>
          </div>
          <Button variant="secondary" icon={<Download className="h-4 w-4" />} onClick={() => void downloadPdf()}>
            Gerar PDF
          </Button>
        </div>
      </Card>

      <div className="grid gap-6 xl:grid-cols-2">
        <Card className="space-y-4">
          <div>
            <h3 className="text-lg font-semibold text-neutral-900">Parâmetros de CQ</h3>
            <p className="text-sm text-neutral-500">Defina se a área usa intervalo fixo ou tolerância percentual.</p>
          </div>

          <div className="flex flex-wrap gap-2">
            {availableAnalitos.slice(0, 9).map((analito) => (
              <button
                key={analito}
                type="button"
                className="rounded-full border border-green-200 bg-white px-3 py-1 text-xs font-medium text-green-800 transition hover:bg-green-50"
                onClick={() => setParameterForm((current) => ({ ...current, analito }))}
              >
                {analito}
              </button>
            ))}
          </div>

          <div className="grid gap-4 md:grid-cols-2">
            <Input
              label="Analito"
              value={parameterForm.analito}
              onChange={(event) => setParameterForm((current) => ({ ...current, analito: event.target.value.toUpperCase() }))}
            />
            <Select
              label="Modo"
              value={parameterForm.modo}
              onChange={(event) =>
                setParameterForm((current) => ({ ...current, modo: event.target.value as AreaQcParameterRequest['modo'] }))
              }
            >
              <option value="INTERVALO">Intervalo</option>
              <option value="PERCENTUAL">Percentual</option>
            </Select>
            <Input
              label="Valor alvo"
              type="number"
              step="0.01"
              value={String(parameterForm.alvoValor)}
              onChange={(event) => setParameterForm((current) => ({ ...current, alvoValor: Number(event.target.value) }))}
            />
            {parameterForm.modo === 'INTERVALO' ? (
              <>
                <Input
                  label="Mínimo"
                  type="number"
                  step="0.01"
                  value={String(parameterForm.minValor ?? 0)}
                  onChange={(event) => setParameterForm((current) => ({ ...current, minValor: Number(event.target.value) }))}
                />
                <Input
                  label="Máximo"
                  type="number"
                  step="0.01"
                  value={String(parameterForm.maxValor ?? 0)}
                  onChange={(event) => setParameterForm((current) => ({ ...current, maxValor: Number(event.target.value) }))}
                />
              </>
            ) : (
              <Input
                label="Tolerância %"
                type="number"
                step="0.01"
                value={String(parameterForm.toleranciaPercentual ?? 0)}
                onChange={(event) =>
                  setParameterForm((current) => ({ ...current, toleranciaPercentual: Number(event.target.value) }))
                }
              />
            )}
            <Input
              label="Equipamento"
              value={parameterForm.equipamento}
              onChange={(event) => setParameterForm((current) => ({ ...current, equipamento: event.target.value }))}
            />
            <Input
              label="Lote controle"
              value={parameterForm.loteControle}
              onChange={(event) => setParameterForm((current) => ({ ...current, loteControle: event.target.value }))}
            />
            <Input
              label="Nível controle"
              value={parameterForm.nivelControle}
              onChange={(event) => setParameterForm((current) => ({ ...current, nivelControle: event.target.value }))}
            />
          </div>

          <Button
            className="w-full"
            icon={<FlaskConical className="h-4 w-4" />}
            onClick={() => void saveParameter()}
            loading={createParameter.isPending}
          >
            Salvar parâmetro
          </Button>
        </Card>

        <Card className="space-y-4">
          <div>
            <h3 className="text-lg font-semibold text-neutral-900">Registrar medição</h3>
            <p className="text-sm text-neutral-500">A aprovação é calculada automaticamente com base no parâmetro ativo.</p>
          </div>

          <div className="grid gap-4 md:grid-cols-2">
            <Input
              label="Data"
              type="date"
              value={measurementForm.dataMedicao}
              onChange={(event) => setMeasurementForm((current) => ({ ...current, dataMedicao: event.target.value }))}
            />
            <Select
              label="Analito"
              value={measurementForm.analito}
              onChange={(event) =>
                setMeasurementForm((current) => ({
                  ...current,
                  analito: event.target.value,
                  parameterId: '',
                }))
              }
            >
              <option value="">Selecione</option>
              {availableAnalitos.map((analito) => (
                <option key={analito} value={analito}>
                  {analito}
                </option>
              ))}
            </Select>
            <Input
              label="Valor medido"
              type="number"
              step="0.01"
              value={String(measurementForm.valorMedido)}
              onChange={(event) => setMeasurementForm((current) => ({ ...current, valorMedido: Number(event.target.value) }))}
            />
            <Input
              label="Equipamento"
              value={measurementForm.equipamento}
              onChange={(event) => setMeasurementForm((current) => ({ ...current, equipamento: event.target.value }))}
              placeholder={selectedParameter?.equipamento ?? 'Opcional'}
            />
            <Input
              label="Lote controle"
              value={measurementForm.loteControle}
              onChange={(event) => setMeasurementForm((current) => ({ ...current, loteControle: event.target.value }))}
              placeholder={selectedParameter?.loteControle ?? 'Opcional'}
            />
            <Input
              label="Nível controle"
              value={measurementForm.nivelControle}
              onChange={(event) => setMeasurementForm((current) => ({ ...current, nivelControle: event.target.value }))}
              placeholder={selectedParameter?.nivelControle ?? 'Opcional'}
            />
          </div>

          {measurementForm.analito ? (
            <div className={getCandidatePanelClasses(measurementCandidates.length, Boolean(selectedParameter))}>
              <div className="text-sm font-medium">
                {getCandidateMessage(measurementForm.analito, measurementCandidates, selectedParameter)}
              </div>
              {selectedParameter ? (
                <div className="mt-2 text-sm text-neutral-700">
                  Parametro selecionado: {formatAreaQcParameterLabel(selectedParameter)}
                </div>
              ) : null}
            </div>
          ) : null}

          {measurementCandidates.length ? (
            <Select
              label="Parâmetro aplicável"
              value={measurementForm.parameterId ?? ''}
              onChange={(event) =>
                setMeasurementForm((current) => ({
                  ...current,
                  parameterId: event.target.value,
                }))
              }
            >
              <option value="">
                {measurementCandidates.length === 1
                  ? 'Deixar o backend usar o único parâmetro ativo'
                  : 'Selecionar explicitamente o parâmetro correto'}
              </option>
              {measurementCandidates.map((parameter) => (
                <option key={parameter.id} value={parameter.id}>
                  {formatAreaQcParameterLabel(parameter)}
                </option>
              ))}
            </Select>
          ) : null}

          <TextArea
            label="Observação"
            value={measurementForm.observacao}
            onChange={(event) => setMeasurementForm((current) => ({ ...current, observacao: event.target.value }))}
            placeholder="Anote desvios, condições da amostra ou observações relevantes."
          />

          {selectedParameter ? (
            <div className="rounded-2xl bg-neutral-50 px-4 py-3 text-sm text-neutral-600">
              Faixa ativa para {selectedParameter.analito}: {selectedParameter.modo === 'INTERVALO'
                ? `${selectedParameter.minValor ?? 0} até ${selectedParameter.maxValor ?? 0}`
                : `${selectedParameter.alvoValor} ± ${selectedParameter.toleranciaPercentual ?? 0}%`}
            </div>
          ) : null}

          <Button className="w-full" onClick={() => void saveMeasurement()} loading={createMeasurement.isPending}>
            Registrar medição
          </Button>

          {measurementError ? (
            <div className="rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
              <div className="font-medium">O backend bloqueou a medição desta área.</div>
              <div className="mt-1">{measurementError}</div>
            </div>
          ) : null}
        </Card>
      </div>

      <div className="grid gap-6 xl:grid-cols-[1.05fr_0.95fr]">
        <Card className="space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-lg font-semibold text-neutral-900">Parâmetros ativos</h3>
              <p className="text-sm text-neutral-500">Os mais recentes ficam disponíveis para validação automática.</p>
            </div>
          </div>

          {parameters.length ? (
            <div className="space-y-3">
              {parameters.map((parameter) => (
                <div key={parameter.id} className="rounded-2xl border border-neutral-200 bg-white p-4">
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <div className="text-base font-semibold text-neutral-900">{parameter.analito}</div>
                      <div className="mt-1 text-sm text-neutral-500">
                        {parameter.modo === 'INTERVALO'
                          ? `Faixa ${parameter.minValor ?? 0} até ${parameter.maxValor ?? 0}`
                          : `Alvo ${parameter.alvoValor} com tolerância ${parameter.toleranciaPercentual ?? 0}%`}
                      </div>
                    </div>
                    <Button
                      variant="ghost"
                      size="sm"
                      icon={<Trash2 className="h-4 w-4" />}
                      onClick={() => void removeParameter(parameter.id)}
                    >
                      Remover
                    </Button>
                  </div>
                  <div className="mt-3 grid gap-2 text-sm text-neutral-600 sm:grid-cols-3">
                    <div>Equipamento: {parameter.equipamento || '—'}</div>
                    <div>Lote: {parameter.loteControle || '—'}</div>
                    <div>Nível: {parameter.nivelControle || '—'}</div>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <EmptyState
              icon={<FlaskConical className="h-8 w-8" />}
              title="Nenhum parâmetro cadastrado"
              description="Cadastre o primeiro parâmetro para liberar o registro de medições desta área."
            />
          )}
        </Card>

        <Card className="space-y-4">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <h3 className="text-lg font-semibold text-neutral-900">Histórico de medições</h3>
              <p className="text-sm text-neutral-500">Filtre por analito quando quiser investigar uma rotina específica.</p>
            </div>
            <Select label="Filtro analito" value={analitoFilter} onChange={(event) => setAnalitoFilter(event.target.value)}>
              <option value="">Todos</option>
              {availableAnalitos.map((analito) => (
                <option key={analito} value={analito}>
                  {analito}
                </option>
              ))}
            </Select>
          </div>

          {measurements.length ? (
            <div className="space-y-3">
              {measurements.map((measurement) => (
                <div key={measurement.id} className="rounded-2xl bg-neutral-50 px-4 py-4">
                  <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                    <div>
                      <div className="font-semibold text-neutral-900">{measurement.analito}</div>
                      <div className="text-sm text-neutral-500">
                        {formatLongBR(measurement.dataMedicao)} · {measurement.modoUsado}
                      </div>
                    </div>
                    <StatusBadge status={measurement.status} />
                  </div>
                  <div className="mt-3 grid gap-2 text-sm text-neutral-600 sm:grid-cols-3">
                    <div>Valor: {measurement.valorMedido.toFixed(2)}</div>
                    <div>Min: {measurement.minAplicado.toFixed(2)}</div>
                    <div>Max: {measurement.maxAplicado.toFixed(2)}</div>
                  </div>
                  <div className="mt-3 grid gap-2 text-sm text-neutral-600 sm:grid-cols-3">
                    <div>Equipamento do parâmetro: {measurement.parameterEquipamento || '—'}</div>
                    <div>Lote do parâmetro: {measurement.parameterLoteControle || '—'}</div>
                    <div>Nível do parâmetro: {measurement.parameterNivelControle || '—'}</div>
                  </div>
                  {measurement.observacao ? (
                    <div className="mt-3 text-sm text-neutral-500">{measurement.observacao}</div>
                  ) : null}
                </div>
              ))}
            </div>
          ) : (
            <EmptyState
              icon={<Microscope className="h-8 w-8" />}
              title="Nenhuma medição registrada"
              description="Assim que a equipe começar a lançar dados, o histórico vai aparecer aqui."
            />
          )}
        </Card>
      </div>
    </div>
  )
}

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  URL.revokeObjectURL(url)
}

function getAreaQcApiErrorMessage(error: unknown, fallbackMessage: string) {
  if (axios.isAxiosError(error)) {
    const apiMessage = error.response?.data?.message
    if (typeof apiMessage === 'string' && apiMessage.trim()) {
      return apiMessage
    }
  }
  return fallbackMessage
}

function formatAreaQcParameterLabel(parameter: AreaQcParameter) {
  const equipamento = parameter.equipamento?.trim() ? `Eq ${parameter.equipamento}` : 'Eq qualquer'
  const lote = parameter.loteControle?.trim() ? `Lote ${parameter.loteControle}` : 'Lote qualquer'
  const nivel = parameter.nivelControle?.trim() ? `Nivel ${parameter.nivelControle}` : 'Nivel qualquer'
  const regra = parameter.modo === 'INTERVALO'
    ? `${parameter.minValor ?? 0} ate ${parameter.maxValor ?? 0}`
    : `${parameter.alvoValor} ± ${parameter.toleranciaPercentual ?? 0}%`
  return `${equipamento} · ${lote} · ${nivel} · ${parameter.modo} · ${regra}`
}

function getCandidateMessage(analito: string, candidates: AreaQcParameter[], selectedParameter: AreaQcParameter | null) {
  if (!candidates.length) {
    return `Nenhum parametro ativo foi encontrado para ${analito}. Cadastre um parametro antes de registrar a medicao.`
  }
  if (selectedParameter) {
    return `A medicao sera rastreada com o parametro selecionado explicitamente para ${analito}.`
  }
  if (candidates.length === 1) {
    return `Existe um unico parametro ativo para ${analito}. O backend consegue rastrear a decisao sem selecao manual.`
  }
  return `Existem ${candidates.length} parametros ativos para ${analito}. Selecione explicitamente o parametro correto ou informe equipamento, lote e nivel suficientes.`
}

function getCandidatePanelClasses(candidateCount: number, hasExplicitSelection: boolean) {
  if (!candidateCount) {
    return 'rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-red-900'
  }
  if (hasExplicitSelection || candidateCount === 1) {
    return 'rounded-2xl border border-green-200 bg-green-50 px-4 py-3 text-green-900'
  }
  return 'rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-amber-900'
}
