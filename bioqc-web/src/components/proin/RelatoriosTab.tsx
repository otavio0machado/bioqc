import { AlertTriangle, CheckCircle2, Download, History, LineChart, RefreshCw } from 'lucide-react'
import { lazy, Suspense, useState } from 'react'
import { useAuth } from '../../hooks/useAuth'
import { useQcExams } from '../../hooks/useQcRecords'
import { useReportHistory } from '../../hooks/useReports'
import { canDownload } from '../../lib/permissions'
import { reportService } from '../../services/reportService'
import { Button, Card, EmptyState, Select, Skeleton, useToast } from '../ui'

const LeveyJenningsModal = lazy(() =>
  import('../charts/LeveyJenningsModal').then((module) => ({ default: module.LeveyJenningsModal })),
)

interface RelatoriosTabProps {
  area: string
}

/** Formata tamanho em bytes (KB/MB) para exibir no historico. */
function formatBytes(bytes?: number | null): string {
  if (!bytes || bytes <= 0) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
}

function formatDuration(ms?: number | null): string {
  if (ms == null) return '—'
  if (ms < 1000) return `${ms} ms`
  return `${(ms / 1000).toFixed(1)} s`
}

function formatDateTime(iso: string): string {
  const d = new Date(iso)
  return d.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })
}

export function RelatoriosTab({ area }: RelatoriosTabProps) {
  const { user } = useAuth()
  const { toast } = useToast()
  const { data: exams = [] } = useQcExams(area)
  const [periodType, setPeriodType] = useState('current-month')
  const [month, setMonth] = useState(String(new Date().getMonth() + 1))
  const [year, setYear] = useState(String(new Date().getFullYear()))
  const [isChartOpen, setIsChartOpen] = useState(false)
  const [isGenerating, setIsGenerating] = useState(false)
  const [lastReport, setLastReport] = useState<{ size: number; elapsedMs: number; filename: string } | null>(null)

  const { data: history = [], isLoading: loadingHistory, refetch: refetchHistory } = useReportHistory(20)

  const handleDownload = async () => {
    setIsGenerating(true)
    const started = performance.now()
    try {
      const blob = await reportService.getQcPdf({ area, periodType, month, year })
      const elapsedMs = Math.round(performance.now() - started)
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      const filename = `qc-report-${area}-${year}-${String(month).padStart(2, '0')}.pdf`
      anchor.href = url
      anchor.download = filename
      anchor.click()
      URL.revokeObjectURL(url)
      setLastReport({ size: blob.size, elapsedMs, filename })
      toast.success(`Relatório gerado (${formatBytes(blob.size)}, ${formatDuration(elapsedMs)}).`)
      refetchHistory()
    } catch {
      toast.warning('Não foi possível gerar o PDF solicitado.')
    } finally {
      setIsGenerating(false)
    }
  }

  return (
    <div className="space-y-6">
      <Card className="space-y-4">
        <div>
          <h3 className="text-lg font-semibold text-neutral-900">Relatórios</h3>
          <p className="text-sm text-neutral-500">Selecione o período e gere os artefatos da rotina de CQ.</p>
        </div>
        <div className="grid gap-4 md:grid-cols-3">
          <Select label="Período" value={periodType} onChange={(event) => setPeriodType(event.target.value)}>
            <option value="current-month">Mês atual</option>
            <option value="specific-month">Mês específico</option>
            <option value="year">Ano</option>
          </Select>
          <InputMonth month={month} setMonth={setMonth} />
          <Select label="Ano" value={year} onChange={(event) => setYear(event.target.value)}>
            {Array.from({ length: 5 }).map((_, index) => {
              const currentYear = new Date().getFullYear() - index
              return (
                <option key={currentYear} value={String(currentYear)}>
                  {currentYear}
                </option>
              )
            })}
          </Select>
        </div>
        <div className="flex flex-wrap gap-3">
          {canDownload(user) ? (
            <Button
              onClick={() => void handleDownload()}
              icon={<Download className="h-4 w-4" />}
              loading={isGenerating}
              disabled={isGenerating}
            >
              Gerar PDF
            </Button>
          ) : null}
          <Button variant="secondary" onClick={() => setIsChartOpen(true)} icon={<LineChart className="h-4 w-4" />}>
            Gerar Gráfico Levey-Jennings
          </Button>
        </div>

        {lastReport ? (
          <div className="flex items-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-900">
            <CheckCircle2 className="h-4 w-4" />
            <span>
              <strong>{lastReport.filename}</strong> · {formatBytes(lastReport.size)} · {formatDuration(lastReport.elapsedMs)}
            </span>
          </div>
        ) : null}
      </Card>

      {/* Historico de relatorios gerados */}
      <Card className="space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <History className="h-4 w-4 text-neutral-500" />
            <h4 className="text-base font-semibold text-neutral-900">Histórico de gerações</h4>
          </div>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => refetchHistory()}
            icon={<RefreshCw className="h-3.5 w-3.5" />}
          >
            Atualizar
          </Button>
        </div>
        {loadingHistory ? (
          <Skeleton height="8rem" />
        ) : history.length === 0 ? (
          <EmptyState
            icon={<History className="h-8 w-8" />}
            title="Nenhum relatório gerado ainda"
            description="A cada PDF gerado, uma linha aparece aqui com autor, tamanho e status."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-neutral-100 text-xs uppercase tracking-wider text-neutral-500">
                  <th className="px-3 py-2.5">Quando</th>
                  <th className="px-3 py-2.5">Tipo</th>
                  <th className="px-3 py-2.5">Área</th>
                  <th className="px-3 py-2.5">Período</th>
                  <th className="px-3 py-2.5">Status</th>
                  <th className="px-3 py-2.5">Tamanho</th>
                  <th className="px-3 py-2.5">Duração</th>
                  <th className="px-3 py-2.5">Usuário</th>
                </tr>
              </thead>
              <tbody>
                {history.map((run) => (
                  <tr key={run.id} className="border-b border-neutral-50 hover:bg-neutral-50/50">
                    <td className="whitespace-nowrap px-3 py-2 text-neutral-700">{formatDateTime(run.createdAt)}</td>
                    <td className="px-3 py-2 text-neutral-700">{run.type === 'QC_PDF' ? 'CQ' : run.type === 'REAGENTS_PDF' ? 'Reagentes' : run.type}</td>
                    <td className="px-3 py-2 text-neutral-600">{run.area ?? '—'}</td>
                    <td className="px-3 py-2 text-neutral-600">
                      {run.year ? `${run.year}` : '—'}{run.month ? `/${String(run.month).padStart(2, '0')}` : ''}
                    </td>
                    <td className="px-3 py-2">
                      {run.status === 'SUCCESS' ? (
                        <span className="inline-flex items-center gap-1 rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-semibold text-emerald-800">
                          <CheckCircle2 className="h-3 w-3" /> Sucesso
                        </span>
                      ) : (
                        <span
                          className="inline-flex items-center gap-1 rounded-full bg-red-100 px-2 py-0.5 text-xs font-semibold text-red-800"
                          title={run.errorMessage ?? ''}
                        >
                          <AlertTriangle className="h-3 w-3" /> Falha
                        </span>
                      )}
                    </td>
                    <td className="px-3 py-2 font-mono text-neutral-600">{formatBytes(run.sizeBytes)}</td>
                    <td className="px-3 py-2 font-mono text-neutral-600">{formatDuration(run.durationMs)}</td>
                    <td className="px-3 py-2 text-neutral-500">{run.username ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {isChartOpen ? (
        <Suspense fallback={null}>
          <LeveyJenningsModal isOpen={isChartOpen} onClose={() => setIsChartOpen(false)} area={area} exams={exams} />
        </Suspense>
      ) : null}
    </div>
  )
}

function InputMonth({ month, setMonth }: { month: string; setMonth: (value: string) => void }) {
  return (
    <Select label="Mês" value={month} onChange={(event) => setMonth(event.target.value)}>
      {Array.from({ length: 12 }).map((_, index) => (
        <option key={index} value={String(index + 1)}>
          {String(index + 1).padStart(2, '0')}
        </option>
      ))}
    </Select>
  )
}
