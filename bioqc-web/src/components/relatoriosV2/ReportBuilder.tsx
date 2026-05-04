import axios from 'axios'
import { AlertTriangle, ArrowLeft, CheckCircle2, Download, FileText, Loader2, PlayCircle } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  useGenerateReportV2,
  usePreviewReportV2,
  useReportDefinitionV2,
  useSignReportV2,
} from '../../hooks/useReportsV2'
import { reportsV2Service } from '../../services/reportsV2Service'
import type {
  ReportCode,
  ReportDefinition,
  ReportExecutionResponse,
  ReportFilterField,
  ReportFormat,
} from '../../types/reportsV2'
import { Button, Card, LoadingSpinner, Select, useToast } from '../ui'
import { DynamicFilterForm } from './DynamicFilterForm'

/**
 * Fluxo F1 de gerar relatorio V2.
 *
 * <p>Arquitetura:
 * <ul>
 *   <li>Filtros a esquerda -&gt; debounce 500ms -&gt; {@code /preview} renderiza HTML no centro.</li>
 *   <li>Botao "Gerar" chama {@code /generate} e, se marcado "Assinar imediatamente",
 *       encadeia {@code /executions/:id/sign} apos o sucesso.</li>
 *   <li>Erros 422 do backend (InvalidFilter/ValidationException) sao exibidos como
 *       banner vermelho preservando a mensagem original.</li>
 * </ul>
 *
 * <p>O HTML devolvido por {@code /preview} vem do backend (server-rendered
 * sem input do cliente) - foi classificado como seguro para
 * {@code dangerouslySetInnerHTML} no architecture_note. Nao mexer sem
 * reabrir a discussao.
 */
export function ReportBuilder() {
  const { code } = useParams<{ code: string }>()
  const navigate = useNavigate()
  const { toast } = useToast()
  const reportCode = code as ReportCode | undefined
  const definitionQuery = useReportDefinitionV2(reportCode)
  const previewMutation = usePreviewReportV2()
  const generateMutation = useGenerateReportV2()
  const signMutation = useSignReportV2()

  const [filters, setFilters] = useState<Record<string, unknown>>({})
  const [format, setFormat] = useState<ReportFormat>('PDF')
  const [signImmediately, setSignImmediately] = useState(false)
  const [lastExecution, setLastExecution] = useState<ReportExecutionResponse | null>(null)
  const [generateError, setGenerateError] = useState<string | null>(null)
  const [isDownloading, setIsDownloading] = useState(false)
  const defaultsInitializedRef = useRef<ReportCode | null>(null)

  // Pre-selecao de format compativel quando definition carregar.
  useEffect(() => {
    const def = definitionQuery.data
    if (def && !def.supportedFormats.includes(format)) {
      setFormat(def.supportedFormats[0] ?? 'PDF')
    }
  }, [definitionQuery.data, format])

  useEffect(() => {
    setFilters({})
    setLastExecution(null)
    setGenerateError(null)
    setSignImmediately(false)
    defaultsInitializedRef.current = null
  }, [reportCode])

  useEffect(() => {
    const def = definitionQuery.data
    if (!def || !reportCode || defaultsInitializedRef.current === reportCode) return
    defaultsInitializedRef.current = reportCode
    setFilters(buildDefaultFilters(def))
  }, [definitionQuery.data, reportCode])

  // Preview debounced. Re-dispara a cada mudanca em filtros, code, ou formato,
  // mas aguarda 500ms sem alteracao para evitar spam. Cancelado pelo cleanup.
  const previewMutateRef = useRef(previewMutation.mutate)
  previewMutateRef.current = previewMutation.mutate
  useEffect(() => {
    const def = definitionQuery.data
    if (!reportCode || !def?.previewSupported || !hasRequiredFilters(def, filters)) return
    const handle = window.setTimeout(() => {
      previewMutateRef.current({ code: reportCode, filters })
    }, 500)
    return () => window.clearTimeout(handle)
  }, [reportCode, filters, definitionQuery.data])

  const previewError = useMemo(() => {
    if (!previewMutation.isError) return null
    return extractErrorMessage(previewMutation.error)
  }, [previewMutation.isError, previewMutation.error])

  const handleGenerate = async () => {
    if (!reportCode) return
    setGenerateError(null)
    const effectiveSign = definition?.signatureRequired ? true : signImmediately
    try {
      const execution = await generateMutation.mutateAsync({
        code: reportCode,
        filters,
        format,
        signImmediately: effectiveSign,
      })

      if (effectiveSign && execution.status !== 'SIGNED') {
        try {
          const signed = await signMutation.mutateAsync({ id: execution.id })
          setLastExecution(signed)
          notifyGenerated(toast, signed, true)
        } catch (signError) {
          setLastExecution(execution)
          toast.warning(
            `Relatorio gerado, mas falha ao assinar: ${extractErrorMessage(signError)}`,
          )
        }
      } else {
        setLastExecution(execution)
        notifyGenerated(toast, execution, false)
      }
    } catch (error) {
      setGenerateError(extractErrorMessage(error))
    }
  }

  const handleDownload = async () => {
    if (!lastExecution) return
    setIsDownloading(true)
    try {
      const result = await reportsV2Service.downloadBlob(lastExecution.id)
      const url = URL.createObjectURL(result.blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download =
        result.filename ?? `${lastExecution.reportNumber ?? lastExecution.id}.pdf`
      anchor.click()
      URL.revokeObjectURL(url)
    } catch (error) {
      toast.error(`Falha ao baixar: ${extractErrorMessage(error)}`)
    } finally {
      setIsDownloading(false)
    }
  }

  if (!reportCode) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-8">
        <Card>
          <p className="text-sm text-neutral-600">Codigo de relatorio ausente.</p>
        </Card>
      </div>
    )
  }

  if (definitionQuery.isLoading) {
    return (
      <div className="flex min-h-60 items-center justify-center">
        <LoadingSpinner size="lg" />
      </div>
    )
  }

  if (definitionQuery.isError || !definitionQuery.data) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-8">
        <Card className="space-y-3">
          <h3 className="text-lg font-semibold text-neutral-900">Relatorio indisponivel</h3>
          <p className="text-sm text-neutral-600">
            {definitionQuery.error
              ? extractErrorMessage(definitionQuery.error)
              : 'Nao foi possivel carregar a definicao desse relatorio.'}
          </p>
          <Button variant="secondary" onClick={() => navigate('/relatorios')} icon={<ArrowLeft className="h-4 w-4" />}>
            Voltar ao catalogo
          </Button>
        </Card>
      </div>
    )
  }

  const definition = definitionQuery.data
  const previewData = previewMutation.data
  const canGenerate = hasRequiredFilters(definition, filters)
  const lastExecutionHasWarnings = (lastExecution?.warnings ?? []).length > 0

  return (
    <div className="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
      <header className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => navigate('/relatorios')}
            icon={<ArrowLeft className="h-4 w-4" />}
          >
            Catalogo
          </Button>
          <div>
            <h1 className="text-2xl font-bold text-neutral-900">{definition.name}</h1>
            <p className="text-sm text-neutral-500">{definition.description}</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {definition.supportedFormats.length > 1 ? (
            <Select value={format} onChange={(event) => setFormat(event.target.value as ReportFormat)}>
              {definition.supportedFormats.map((item) => (
                <option key={item} value={item}>
                  {item}
                </option>
              ))}
            </Select>
          ) : null}
          <label
            className={
              'flex items-center gap-2 rounded-xl border bg-white px-3 py-2 text-sm transition ' +
              (definition.signatureRequired
                ? 'border-purple-300 text-purple-800'
                : 'border-neutral-200 text-neutral-700')
            }
            title={
              definition.signatureRequired
                ? 'Este relatório exige assinatura obrigatória pelo responsável técnico.'
                : undefined
            }
          >
            <input
              type="checkbox"
              checked={definition.signatureRequired ? true : signImmediately}
              disabled={definition.signatureRequired}
              onChange={(event) => setSignImmediately(event.target.checked)}
              className="h-4 w-4 rounded border-neutral-300 text-green-800 focus:ring-green-800 disabled:opacity-60"
            />
            {definition.signatureRequired ? 'Assinatura obrigatória' : 'Assinar imediatamente'}
          </label>
          <Button
            onClick={() => void handleGenerate()}
            loading={generateMutation.isPending || signMutation.isPending}
            disabled={!canGenerate}
            icon={<PlayCircle className="h-4 w-4" />}
          >
            Gerar {format}
          </Button>
        </div>
      </header>

      <div className="grid gap-6 lg:grid-cols-[22rem_1fr]">
        <aside>
          <Card className="space-y-4">
            <div>
              <h2 className="text-base font-semibold text-neutral-900">Filtros</h2>
              <p className="text-xs text-neutral-500">
                Campos obrigatorios sao marcados com <span className="font-semibold">*</span>.
              </p>
            </div>
            <DynamicFilterForm
              filterSpec={definition.filterSpec}
              values={filters}
              onChange={setFilters}
            />
            <div className="rounded-xl border border-neutral-200 bg-neutral-50 p-3 text-xs text-neutral-600">
              <p className="font-semibold text-neutral-700">Base legal</p>
              <p className="mt-1">{definition.legalBasis}</p>
            </div>
          </Card>
        </aside>

        <section className="space-y-4">
          {generateError ? (
            <div className="flex items-start gap-3 rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
              <div>
                <p className="font-semibold">Falha ao gerar relatorio</p>
                <p className="mt-1">{generateError}</p>
              </div>
            </div>
          ) : null}

          {lastExecution ? (
            <div className={
              'flex items-center justify-between gap-3 rounded-2xl border p-4 text-sm ' +
              (lastExecutionHasWarnings
                ? 'border-amber-200 bg-amber-50 text-amber-950'
                : 'border-emerald-200 bg-emerald-50 text-emerald-900')
            }>
              <div className="flex items-center gap-2">
                {lastExecutionHasWarnings
                  ? <AlertTriangle className="h-4 w-4 shrink-0" />
                  : <CheckCircle2 className="h-4 w-4 shrink-0" />}
                <div>
                  <p className="font-semibold">
                    {lastExecution.reportNumber ?? 'Relatorio'} {' '}
                    {lastExecution.status === 'SIGNED' ? '(assinado)' : ''}
                    {lastExecutionHasWarnings ? '(com avisos)' : ''}
                  </p>
                  <p className="text-xs opacity-80">
                    Periodo: {lastExecution.periodLabel ?? '-'}
                    {lastExecution.createdAt ? ` · Gerado em ${new Date(lastExecution.createdAt).toLocaleString('pt-BR')}` : ''}
                  </p>
                  {lastExecutionHasWarnings ? (
                    <ul className="mt-2 list-disc space-y-0.5 pl-5 text-xs">
                      {lastExecution.warnings.map((warning, idx) => (
                        <li key={idx}>{warning}</li>
                      ))}
                    </ul>
                  ) : null}
                </div>
              </div>
              <Button
                size="sm"
                variant="secondary"
                onClick={() => void handleDownload()}
                icon={<Download className="h-4 w-4" />}
                loading={isDownloading}
              >
                Baixar PDF
              </Button>
            </div>
          ) : null}

          <Card className="min-h-[24rem] space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-base font-semibold text-neutral-900">Preview</h2>
                {previewData?.periodLabel ? (
                  <p className="text-xs text-neutral-500">Periodo: {previewData.periodLabel}</p>
                ) : null}
              </div>
              {previewMutation.isPending ? (
                <span className="inline-flex items-center gap-1 text-xs text-neutral-500">
                  <Loader2 className="h-3 w-3 animate-spin" /> Atualizando...
                </span>
              ) : null}
            </div>

            {previewData && previewData.warnings.length > 0 ? (
              <div className="rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
                <p className="font-semibold">Avisos</p>
                <ul className="mt-1 list-disc space-y-0.5 pl-5">
                  {previewData.warnings.map((warning, idx) => (
                    <li key={idx}>{warning}</li>
                  ))}
                </ul>
              </div>
            ) : null}

            {previewError ? (
              <div className="rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-800">
                {previewError}
              </div>
            ) : null}

            {previewData ? (
              <div
                className="max-h-[60vh] overflow-auto rounded-xl border border-neutral-200 bg-white p-4 text-sm text-neutral-800"
                // Conteudo server-rendered: backend produz HTML safe a partir
                // de dados do dominio. Documentado no architecture_note.
                dangerouslySetInnerHTML={{ __html: previewData.html }}
              />
            ) : !previewMutation.isPending ? (
              <div className="flex min-h-56 flex-col items-center justify-center gap-2 text-center text-neutral-500">
                <FileText className="h-8 w-8" />
                <p className="text-sm">
                  {canGenerate
                    ? 'Preview sera atualizado automaticamente.'
                    : 'Preencha os filtros obrigatorios para visualizar o preview.'}
                </p>
              </div>
            ) : null}
          </Card>
        </section>
      </div>
    </div>
  )
}

function buildDefaultFilters(definition: ReportDefinition): Record<string, unknown> {
  const defaults: Record<string, unknown> = {}
  for (const field of definition.filterSpec.fields) {
    if (field.key === 'periodType' && field.allowedValues?.includes('current-month')) {
      defaults[field.key] = 'current-month'
    } else if (field.key === 'area' && field.allowedValues?.includes('bioquimica')) {
      defaults[field.key] = 'bioquimica'
    } else if (field.key === 'areas' && field.required && field.allowedValues?.length) {
      defaults[field.key] = field.allowedValues
    } else if (field.required && field.type === 'STRING_ENUM' && field.allowedValues?.length === 1) {
      defaults[field.key] = field.allowedValues[0]
    }
  }
  return defaults
}

function hasRequiredFilters(definition: ReportDefinition, values: Record<string, unknown>): boolean {
  for (const field of definition.filterSpec.fields) {
    if (field.required && !hasValue(values[field.key], field)) return false
  }

  const periodType = values.periodType
  if (periodType === 'specific-month') {
    return hasValue(values.month) && hasValue(values.year)
  }
  if (periodType === 'year') {
    return hasValue(values.year)
  }
  if (periodType === 'date-range') {
    return hasValue(values.dateFrom) && hasValue(values.dateTo)
  }
  return true
}

function hasValue(value: unknown, field?: ReportFilterField): boolean {
  if (Array.isArray(value)) return field?.required ? value.length > 0 : true
  return value !== undefined && value !== null && !(typeof value === 'string' && value.trim() === '')
}

function notifyGenerated(
  toast: ReturnType<typeof useToast>['toast'],
  execution: ReportExecutionResponse,
  signed: boolean,
) {
  const reportNumber = execution.reportNumber ?? ''
  if ((execution.warnings ?? []).length > 0 || execution.status === 'WITH_WARNINGS') {
    toast.warning(`Relatorio ${reportNumber} gerado com avisos.`)
    return
  }
  toast.success(`Relatorio ${reportNumber} gerado${signed ? ' e assinado' : ''}.`)
}

function extractErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as
      | {
          message?: string
          error?: string
          detail?: string
          title?: string
          violations?: Array<{ field?: string; message?: string } | string>
        }
      | undefined
    if (data?.violations && data.violations.length > 0) {
      return data.violations
        .map((v) => (typeof v === 'string' ? v : v.field ? `${v.field}: ${v.message ?? ''}` : v.message ?? ''))
        .filter(Boolean)
        .join('; ')
    }
    return data?.detail ?? data?.message ?? data?.title ?? data?.error ?? error.message
  }
  if (error instanceof Error) return error.message
  return 'Erro desconhecido'
}
