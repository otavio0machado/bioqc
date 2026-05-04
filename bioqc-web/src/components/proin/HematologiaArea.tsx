import { Activity, Download, PlusCircle, TestTube2 } from 'lucide-react'
import { useMemo, useState, type Dispatch, type SetStateAction } from 'react'
import {
  useCreateHematologyBioRecord,
  useCreateHematologyMeasurement,
  useCreateHematologyParameter,
  useHematologyBioRecords,
  useHematologyMeasurements,
  useHematologyParameters,
} from '../../hooks/useHematology'
import { reportService } from '../../services/reportService'
import type {
  HematologyBioRequest,
  HematologyMeasurementRequest,
  HematologyParameterRequest,
} from '../../types'
import { Button, Card, EmptyState, Input, Modal, Select, StatusBadge, useToast } from '../ui'
import { formatLongBR } from '../../utils/date'

const analytes = [
  { key: 'Hemacias', label: 'Hemácias', shortLabel: 'RBC' },
  { key: 'Hematocrito', label: 'Hematócrito', shortLabel: 'HCT' },
  { key: 'Hemoglobina', label: 'Hemoglobina', shortLabel: 'HGB' },
  { key: 'Leucocitos', label: 'Leucócitos', shortLabel: 'WBC' },
  { key: 'Plaquetas', label: 'Plaquetas', shortLabel: 'PLT' },
  { key: 'Rdw', label: 'RDW', shortLabel: 'RDW' },
  { key: 'Vpm', label: 'VPM', shortLabel: 'VPM' },
] as const

const emptyParameter: HematologyParameterRequest = {
  analito: 'RBC',
  equipamento: '',
  loteControle: '',
  nivelControle: '',
  modo: 'INTERVALO',
  alvoValor: 0,
  minValor: 0,
  maxValor: 0,
  toleranciaPercentual: 0,
}

const createMeasurementForm = (): HematologyMeasurementRequest => ({
  parameterId: '',
  dataMedicao: new Date().toISOString().slice(0, 10),
  analito: '',
  valorMedido: 0,
  observacao: '',
})

function createEmptyBioForm(): HematologyBioRequest {
  return {
    dataBio: new Date().toISOString().slice(0, 10),
    dataPad: '',
    registroBio: '',
    registroPad: '',
    modoCi: 'bio',
    bioHemacias: 0,
    bioHematocrito: 0,
    bioHemoglobina: 0,
    bioLeucocitos: 0,
    bioPlaquetas: 0,
    bioRdw: 0,
    bioVpm: 0,
    padHemacias: 0,
    padHematocrito: 0,
    padHemoglobina: 0,
    padLeucocitos: 0,
    padPlaquetas: 0,
    padRdw: 0,
    padVpm: 0,
    ciMinHemacias: 0,
    ciMaxHemacias: 0,
    ciMinHematocrito: 0,
    ciMaxHematocrito: 0,
    ciMinHemoglobina: 0,
    ciMaxHemoglobina: 0,
    ciMinLeucocitos: 0,
    ciMaxLeucocitos: 0,
    ciMinPlaquetas: 0,
    ciMaxPlaquetas: 0,
    ciMinRdw: 0,
    ciMaxRdw: 0,
    ciMinVpm: 0,
    ciMaxVpm: 0,
    ciPctHemacias: 0,
    ciPctHematocrito: 0,
    ciPctHemoglobina: 0,
    ciPctLeucocitos: 0,
    ciPctPlaquetas: 0,
    ciPctRdw: 0,
    ciPctVpm: 0,
  }
}

export function HematologiaArea() {
  const { toast } = useToast()
  const [parameterForm, setParameterForm] = useState<HematologyParameterRequest>(emptyParameter)
  const [measurementForm, setMeasurementForm] = useState<HematologyMeasurementRequest>(createMeasurementForm())
  const [bioForm, setBioForm] = useState<HematologyBioRequest>(createEmptyBioForm())
  const [isBioModalOpen, setIsBioModalOpen] = useState(false)
  const { data: parameters = [] } = useHematologyParameters()
  const { data: measurements = [] } = useHematologyMeasurements()
  const { data: bioRecords = [] } = useHematologyBioRecords()
  const createParameter = useCreateHematologyParameter()
  const createMeasurement = useCreateHematologyMeasurement()
  const createBioRecord = useCreateHematologyBioRecord()

  const activeParameter = useMemo(
    () => parameters.find((item) => item.id === measurementForm.parameterId),
    [measurementForm.parameterId, parameters],
  )

  const saveParameter = async () => {
    if (!parameterForm.analito) {
      toast.warning('Informe o analito do parâmetro.')
      return
    }
    try {
      await createParameter.mutateAsync(parameterForm)
      toast.success('Parâmetro de hematologia salvo.')
      setParameterForm(emptyParameter)
    } catch {
      toast.error('Não foi possível salvar o parâmetro.')
    }
  }

  const saveMeasurement = async () => {
    if (!measurementForm.parameterId || !measurementForm.valorMedido) {
      toast.warning('Selecione o parâmetro e informe o valor medido.')
      return
    }
    try {
      const response = await createMeasurement.mutateAsync({
        ...measurementForm,
        analito: activeParameter?.analito ?? measurementForm.analito,
      })
      toast[response.status === 'APROVADO' ? 'success' : 'warning'](`Medição registrada: ${response.status}.`)
      setMeasurementForm(createMeasurementForm())
    } catch {
      toast.error('Não foi possível registrar a medição.')
    }
  }

  const saveBioRecord = async () => {
    try {
      await createBioRecord.mutateAsync(bioForm)
      toast.success('Registro Bio x CI salvo.')
      setBioForm(createEmptyBioForm())
      setIsBioModalOpen(false)
    } catch {
      toast.error('Não foi possível salvar o registro Bio x CI.')
    }
  }

  const downloadPdf = async () => {
    try {
      const blob = await reportService.getQcPdf({
        area: 'hematologia',
        periodType: 'current-month',
        month: String(new Date().getMonth() + 1),
        year: String(new Date().getFullYear()),
      })
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = 'hematologia-report.pdf'
      anchor.click()
      URL.revokeObjectURL(url)
    } catch {
      toast.error('Não foi possível gerar o PDF de hematologia.')
    }
  }

  return (
    <div className="space-y-6">
      <Card className="bg-gradient-to-r from-white to-green-50">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div className="space-y-2">
            <div className="inline-flex items-center gap-2 rounded-full bg-green-100 px-3 py-1 text-xs font-semibold uppercase tracking-[0.16em] text-green-800">
              <Activity className="h-3.5 w-3.5" />
              Hematologia Especializada
            </div>
            <h2 className="text-2xl font-semibold text-neutral-900">Hematologia</h2>
            <p className="max-w-3xl text-sm text-neutral-600">
              Módulo com parâmetros por intervalo ou percentual, medições aprovadas automaticamente e rotina Bio x Controle Interno.
            </p>
          </div>
          <div className="flex flex-wrap gap-3">
            <Button variant="secondary" icon={<Download className="h-4 w-4" />} onClick={() => void downloadPdf()}>
              Gerar PDF
            </Button>
            <Button icon={<PlusCircle className="h-4 w-4" />} onClick={() => setIsBioModalOpen(true)}>
              Novo Bio x CI
            </Button>
          </div>
        </div>
      </Card>

      <div className="grid gap-6 xl:grid-cols-2">
        <Card className="space-y-4">
          <div>
            <h3 className="text-lg font-semibold text-neutral-900">Parâmetros de hematologia</h3>
            <p className="text-sm text-neutral-500">Use intervalo fixo ou tolerância percentual para cada analito.</p>
          </div>
          <div className="grid gap-4 md:grid-cols-2">
            <Select
              label="Analito"
              value={parameterForm.analito}
              onChange={(event) => setParameterForm((current) => ({ ...current, analito: event.target.value }))}
            >
              {['RBC', 'HGB', 'HCT', 'WBC', 'PLT', 'RDW', 'VPM'].map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </Select>
            <Select
              label="Modo"
              value={parameterForm.modo}
              onChange={(event) =>
                setParameterForm((current) => ({ ...current, modo: event.target.value as HematologyParameterRequest['modo'] }))
              }
            >
              <option value="INTERVALO">Intervalo</option>
              <option value="PERCENTUAL">Percentual</option>
            </Select>
            <Input
              label="Valor alvo"
              type="number"
              step="0.01"
              value={String(parameterForm.alvoValor ?? 0)}
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
          <Button className="w-full" onClick={() => void saveParameter()} loading={createParameter.isPending}>
            Salvar parâmetro
          </Button>
        </Card>

        <Card className="space-y-4">
          <div>
            <h3 className="text-lg font-semibold text-neutral-900">Medições</h3>
            <p className="text-sm text-neutral-500">Selecione o parâmetro ativo para calcular a aprovação automaticamente.</p>
          </div>
          <div className="grid gap-4 md:grid-cols-2">
            <Select
              label="Parâmetro"
              value={measurementForm.parameterId}
              onChange={(event) => {
                const parameterId = event.target.value
                const parameter = parameters.find((item) => item.id === parameterId)
                setMeasurementForm((current) => ({
                  ...current,
                  parameterId,
                  analito: parameter?.analito ?? '',
                }))
              }}
            >
              <option value="">Selecione</option>
              {parameters.map((parameter) => (
                <option key={parameter.id} value={parameter.id}>
                  {parameter.analito} · {parameter.modo}
                </option>
              ))}
            </Select>
            <Input
              label="Data"
              type="date"
              value={measurementForm.dataMedicao}
              onChange={(event) => setMeasurementForm((current) => ({ ...current, dataMedicao: event.target.value }))}
            />
            <Input
              label="Analito"
              value={measurementForm.analito}
              onChange={(event) => setMeasurementForm((current) => ({ ...current, analito: event.target.value }))}
            />
            <Input
              label="Valor medido"
              type="number"
              step="0.01"
              value={String(measurementForm.valorMedido)}
              onChange={(event) => setMeasurementForm((current) => ({ ...current, valorMedido: Number(event.target.value) }))}
            />
            <Input
              label="Observação"
              value={measurementForm.observacao}
              onChange={(event) => setMeasurementForm((current) => ({ ...current, observacao: event.target.value }))}
            />
          </div>
          {activeParameter ? (
            <div className="rounded-2xl bg-neutral-50 px-4 py-3 text-sm text-neutral-600">
              Faixa aplicada: {activeParameter.modo === 'INTERVALO'
                ? `${activeParameter.minValor} até ${activeParameter.maxValor}`
                : `${activeParameter.alvoValor} ± ${activeParameter.toleranciaPercentual}%`}
            </div>
          ) : null}
          <Button className="w-full" onClick={() => void saveMeasurement()} loading={createMeasurement.isPending}>
            Registrar medição
          </Button>
        </Card>
      </div>

      <div className="grid gap-6 xl:grid-cols-[1.05fr_0.95fr]">
        <Card className="space-y-4">
          <div>
            <h3 className="text-lg font-semibold text-neutral-900">Parâmetros ativos</h3>
            <p className="text-sm text-neutral-500">Visão consolidada dos parâmetros atualmente cadastrados.</p>
          </div>
          {parameters.length ? (
            <div className="space-y-3">
              {parameters.map((parameter) => (
                <div key={parameter.id} className="rounded-2xl border border-neutral-200 bg-white p-4">
                  <div className="font-semibold text-neutral-900">{parameter.analito}</div>
                  <div className="mt-1 text-sm text-neutral-500">
                    {parameter.modo === 'INTERVALO'
                      ? `Faixa ${parameter.minValor} até ${parameter.maxValor}`
                      : `Alvo ${parameter.alvoValor} ± ${parameter.toleranciaPercentual}%`}
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
              icon={<TestTube2 className="h-8 w-8" />}
              title="Nenhum parâmetro de hematologia"
              description="Cadastre parâmetros para habilitar o registro de medições e o PDF da área."
            />
          )}
        </Card>

        <Card className="space-y-4">
          <div>
            <h3 className="text-lg font-semibold text-neutral-900">Últimas medições</h3>
            <p className="text-sm text-neutral-500">As aprovações e reprovações ficam visíveis logo após o registro.</p>
          </div>
          {measurements.length ? (
            <div className="space-y-3">
              {measurements.map((measurement) => (
                <div key={measurement.id} className="rounded-2xl bg-neutral-50 px-4 py-4">
                  <div className="flex items-center justify-between gap-3">
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
                  {(measurement.parameterEquipamento || measurement.parameterLoteControle || measurement.parameterNivelControle) ? (
                    <div className="mt-2 grid gap-2 text-xs text-neutral-400 sm:grid-cols-3">
                      <div>Equip: {measurement.parameterEquipamento || '—'}</div>
                      <div>Lote: {measurement.parameterLoteControle || '—'}</div>
                      <div>Nível: {measurement.parameterNivelControle || '—'}</div>
                    </div>
                  ) : null}
                </div>
              ))}
            </div>
          ) : (
            <EmptyState
              icon={<Activity className="h-8 w-8" />}
              title="Nenhuma medição"
              description="Assim que um analito for medido, o histórico de hematologia vai aparecer aqui."
            />
          )}
        </Card>
      </div>

      <Card className="space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="text-lg font-semibold text-neutral-900">Bio x Controle Interno</h3>
            <p className="text-sm text-neutral-500">Registros comparativos para os principais analitos da hematologia.</p>
          </div>
          <Button variant="secondary" onClick={() => setIsBioModalOpen(true)}>
            Novo registro
          </Button>
        </div>
        {bioRecords.length ? (
          <div className="grid gap-3 lg:grid-cols-2">
            {bioRecords.map((record) => (
              <div key={record.id} className="rounded-2xl border border-neutral-200 bg-white p-4">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <div className="font-semibold text-neutral-900">
                      {formatLongBR(record.dataBio)}
                    </div>
                    <div className="text-sm text-neutral-500">{record.modoCi}</div>
                  </div>
                  <div className="text-sm text-neutral-500">{record.registroBio || 'Sem lote Bio'}</div>
                </div>
                <div className="mt-3 grid gap-2 text-sm text-neutral-600 sm:grid-cols-2">
                  <div>RBC: {record.bioHemacias.toFixed(2)}</div>
                  <div>HGB: {record.bioHemoglobina.toFixed(2)}</div>
                  <div>WBC: {record.bioLeucocitos.toFixed(2)}</div>
                  <div>PLT: {record.bioPlaquetas.toFixed(2)}</div>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <EmptyState
            icon={<TestTube2 className="h-8 w-8" />}
            title="Nenhum registro Bio x CI"
            description="Cadastre registros comparativos para acompanhar a rotina da hematologia."
          />
        )}
      </Card>

      <HematologyBioModal
        form={bioForm}
        isOpen={isBioModalOpen}
        isSaving={createBioRecord.isPending}
        onClose={() => setIsBioModalOpen(false)}
        onSave={() => void saveBioRecord()}
        setForm={setBioForm}
      />
    </div>
  )
}

interface HematologyBioModalProps {
  form: HematologyBioRequest
  isOpen: boolean
  isSaving: boolean
  onClose: () => void
  onSave: () => void
  setForm: Dispatch<SetStateAction<HematologyBioRequest>>
}

function HematologyBioModal({ form, isOpen, isSaving, onClose, onSave, setForm }: HematologyBioModalProps) {
  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Novo registro Bio x CI"
      size="lg"
      footer={
        <div className="flex justify-end gap-3">
          <Button variant="ghost" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={onSave} loading={isSaving}>
            Salvar registro
          </Button>
        </div>
      }
    >
      <div className="space-y-6">
        <div className="grid gap-4 md:grid-cols-3">
          <Input
            label="Data Bio"
            type="date"
            value={form.dataBio}
            onChange={(event) => setForm((current) => ({ ...current, dataBio: event.target.value }))}
          />
          <Input
            label="Data PAD"
            type="date"
            value={form.dataPad}
            onChange={(event) => setForm((current) => ({ ...current, dataPad: event.target.value }))}
          />
          <Select
            label="Modo CI"
            value={form.modoCi}
            onChange={(event) => setForm((current) => ({ ...current, modoCi: event.target.value }))}
          >
            <option value="bio">Bio</option>
            <option value="intervalo">Intervalo</option>
            <option value="porcentagem">Porcentagem</option>
          </Select>
          <Input
            label="Registro Bio"
            value={form.registroBio}
            onChange={(event) => setForm((current) => ({ ...current, registroBio: event.target.value }))}
          />
          <Input
            label="Registro PAD"
            value={form.registroPad}
            onChange={(event) => setForm((current) => ({ ...current, registroPad: event.target.value }))}
          />
        </div>

        <div className="space-y-4">
          {analytes.map((analyte) => {
            const bioKey = `bio${analyte.key}` as keyof HematologyBioRequest
            const padKey = `pad${analyte.key}` as keyof HematologyBioRequest
            const minKey = `ciMin${analyte.key}` as keyof HematologyBioRequest
            const maxKey = `ciMax${analyte.key}` as keyof HematologyBioRequest
            const pctKey = `ciPct${analyte.key}` as keyof HematologyBioRequest

            return (
              <div key={analyte.key} className="rounded-2xl border border-neutral-200 p-4">
                <div className="mb-3 text-sm font-semibold uppercase tracking-[0.16em] text-neutral-500">
                  {analyte.label}
                </div>
                <div className="grid gap-4 md:grid-cols-4">
                  <Input
                    label={`${analyte.shortLabel} Bio`}
                    type="number"
                    step="0.01"
                    value={String(form[bioKey] ?? 0)}
                    onChange={(event) =>
                      setForm((current) => ({ ...current, [bioKey]: Number(event.target.value) }))
                    }
                  />
                  <Input
                    label={`${analyte.shortLabel} PAD`}
                    type="number"
                    step="0.01"
                    value={String(form[padKey] ?? 0)}
                    onChange={(event) =>
                      setForm((current) => ({ ...current, [padKey]: Number(event.target.value) }))
                    }
                  />
                  {form.modoCi === 'intervalo' ? (
                    <>
                      <Input
                        label="CI Mín"
                        type="number"
                        step="0.01"
                        value={String(form[minKey] ?? 0)}
                        onChange={(event) =>
                          setForm((current) => ({ ...current, [minKey]: Number(event.target.value) }))
                        }
                      />
                      <Input
                        label="CI Máx"
                        type="number"
                        step="0.01"
                        value={String(form[maxKey] ?? 0)}
                        onChange={(event) =>
                          setForm((current) => ({ ...current, [maxKey]: Number(event.target.value) }))
                        }
                      />
                    </>
                  ) : form.modoCi === 'porcentagem' ? (
                    <Input
                      label="CI %"
                      type="number"
                      step="0.01"
                      value={String(form[pctKey] ?? 0)}
                      onChange={(event) =>
                        setForm((current) => ({ ...current, [pctKey]: Number(event.target.value) }))
                      }
                    />
                  ) : null}
                </div>
              </div>
            )
          })}
        </div>
      </div>
    </Modal>
  )
}
