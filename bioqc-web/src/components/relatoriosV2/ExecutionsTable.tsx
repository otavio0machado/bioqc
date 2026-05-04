import {
  AlertTriangle,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Copy,
  Download,
  FileSignature,
  Link as LinkIcon,
  RefreshCw,
  ShieldCheck,
  Tag,
  X,
} from 'lucide-react'
import { useMemo, useState } from 'react'
import { useAuth } from '../../hooks/useAuth'
import { useReportExecutions } from '../../hooks/useReportsV2'
import { reportsV2Service } from '../../services/reportsV2Service'
import type {
  ExecutionsFilter,
  ReportCode,
  ReportDefinition,
  ReportExecutionResponse,
} from '../../types/reportsV2'
import { Button, Card, EmptyState, Input, Select, Skeleton, useToast } from '../ui'
import { SignReportModal } from './SignReportModal'
import { LabelsManagerModal } from './LabelsManagerModal'
import { REPORT_LABELS } from './reportLabels'

interface ExecutionsTableProps {
  definitions: ReportDefinition[]
}

const PAGE_SIZE = 20

/**
 * Tabela de historico de execucoes V2 com filtros (code, status, periodo),
 * paginacao e acoes por linha (baixar, assinar, copiar link de verificacao).
 *
 * Listagem usa {@code GET /api/reports/v2/executions}. Backend aplica RBAC:
 * FUNCIONARIO ve apenas as proprias execucoes; ADMIN/VIGILANCIA veem todas.
 */
export function ExecutionsTable({ definitions }: ExecutionsTableProps) {
  const { user } = useAuth()
  const { toast } = useToast()
  const canSign = user?.role === 'ADMIN' || user?.role === 'VIGILANCIA_SANITARIA'

  const [filter, setFilter] = useState<ExecutionsFilter>({ page: 0, size: PAGE_SIZE })
  const [selected, setSelected] = useState<ReportExecutionResponse | null>(null)
  const [signTarget, setSignTarget] = useState<ReportExecutionResponse | null>(null)
  const [labelsTarget, setLabelsTarget] = useState<ReportExecutionResponse | null>(null)

  const query = useReportExecutions(filter)
  const page = query.data
  const rows = page?.content ?? []

  const codeOptions = useMemo(
    () =>
      definitions.map((def) => ({ value: def.code, label: def.name })).sort((a, b) =>
        a.label.localeCompare(b.label),
      ),
    [definitions],
  )

  const handleDownload = async (execution: ReportExecutionResponse) => {
    try {
      const result = await reportsV2Service.downloadBlob(execution.id)
      const url = URL.createObjectURL(result.blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download =
        result.filename ?? `${execution.reportNumber ?? execution.id}.pdf`
      anchor.click()
      URL.revokeObjectURL(url)
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Erro desconhecido'
      toast.error(`Falha ao baixar: ${message}`)
    }
  }

  const handleCopyVerify = async (execution: ReportExecutionResponse) => {
    const url = execution.verifyUrl
    if (!url) {
      toast.warning('Essa execucao ainda nao possui link publico de verificacao.')
      return
    }
    try {
      await navigator.clipboard.writeText(url)
      toast.success('Link de verificacao copiado.')
    } catch {
      toast.warning('Nao foi possivel copiar. Use o drawer de detalhes.')
    }
  }

  return (
    <div className="space-y-4">
      <Card className="space-y-4">
        <header className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 className="text-lg font-semibold text-neutral-900">Historico V2</h2>
            <p className="text-sm text-neutral-500">
              Todas as execucoes do catalogo V2 com paginacao e filtros.
            </p>
          </div>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => void query.refetch()}
            icon={<RefreshCw className="h-3.5 w-3.5" />}
          >
            Atualizar
          </Button>
        </header>

        <div className="grid gap-3 md:grid-cols-4">
          <Select
            label="Tipo"
            value={filter.code ?? ''}
            onChange={(event) =>
              setFilter((current) => ({
                ...current,
                page: 0,
                code: event.target.value ? (event.target.value as ReportCode) : undefined,
              }))
            }
          >
            <option value="">Todos</option>
            {codeOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
          <Select
            label="Status"
            value={filter.status ?? ''}
            onChange={(event) =>
              setFilter((current) => ({
                ...current,
                page: 0,
                status: event.target.value || undefined,
              }))
            }
          >
            <option value="">Todos</option>
            <option value="SUCCESS">Sucesso</option>
            <option value="WITH_WARNINGS">Com avisos</option>
            <option value="SIGNED">Assinado</option>
            <option value="FAILURE">Falha</option>
          </Select>
          <Input
            label="De"
            type="date"
            value={filter.from?.slice(0, 10) ?? ''}
            onChange={(event) =>
              setFilter((current) => ({
                ...current,
                page: 0,
                from: event.target.value ? `${event.target.value}T00:00:00Z` : undefined,
              }))
            }
          />
          <Input
            label="Ate"
            type="date"
            value={filter.to?.slice(0, 10) ?? ''}
            onChange={(event) =>
              setFilter((current) => ({
                ...current,
                page: 0,
                to: event.target.value ? `${event.target.value}T23:59:59Z` : undefined,
              }))
            }
          />
        </div>
      </Card>

      <Card>
        {query.isLoading ? (
          <Skeleton height="12rem" />
        ) : query.isError ? (
          <div className="flex flex-col items-center justify-center gap-3 rounded-2xl border border-red-100 bg-red-50 px-4 py-10 text-center text-red-900">
            <AlertTriangle className="h-8 w-8" />
            <div>
              <p className="font-semibold">Nao foi possivel carregar o historico V2</p>
              <p className="mt-1 text-sm text-red-800/80">
                {extractErrorMessage(query.error)}
              </p>
            </div>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => void query.refetch()}
              icon={<RefreshCw className="h-3.5 w-3.5" />}
            >
              Tentar novamente
            </Button>
          </div>
        ) : rows.length === 0 ? (
          <EmptyState
            icon={<FileSignature className="h-8 w-8" />}
            title="Nenhuma execucao encontrada"
            description="Ajuste os filtros ou gere um novo relatorio a partir do catalogo."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-neutral-100 text-xs uppercase tracking-wider text-neutral-500">
                  <th className="px-3 py-2.5">Numero</th>
                  <th className="px-3 py-2.5">Tipo</th>
                  <th className="px-3 py-2.5">Periodo</th>
                  <th className="px-3 py-2.5">Gerado por</th>
                  <th className="px-3 py-2.5">Data</th>
                  <th className="px-3 py-2.5">Status</th>
                  <th className="px-3 py-2.5">Etiquetas</th>
                  <th className="px-3 py-2.5">Tamanho</th>
                  <th className="px-3 py-2.5">Ações</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((execution) => (
                  <tr
                    key={execution.id}
                    className="cursor-pointer border-b border-neutral-50 hover:bg-neutral-50/60"
                    onClick={() => setSelected(execution)}
                  >
                    <td className="whitespace-nowrap px-3 py-2 font-mono text-neutral-700">
                      {execution.reportNumber ?? '-'}
                    </td>
                    <td className="px-3 py-2 text-neutral-700">
                      {nameOfCode(execution.reportCode, definitions)}
                    </td>
                    <td className="px-3 py-2 text-neutral-600">{execution.periodLabel ?? '-'}</td>
                    <td className="px-3 py-2 text-neutral-600">{execution.username ?? '-'}</td>
                    <td className="whitespace-nowrap px-3 py-2 text-neutral-600">
                      {new Date(execution.createdAt).toLocaleString('pt-BR', {
                        dateStyle: 'short',
                        timeStyle: 'short',
                      })}
                    </td>
                    <td className="px-3 py-2">{renderStatus(execution)}</td>
                    <td className="px-3 py-2">{renderLabels(execution.labels)}</td>
                    <td className="px-3 py-2 font-mono text-neutral-600">
                      {formatBytes(execution.sizeBytes)}
                    </td>
                    <td
                      className="px-3 py-2"
                      onClick={(event) => event.stopPropagation()}
                    >
                      <div className="flex items-center gap-1">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => void handleDownload(execution)}
                          icon={<Download className="h-3.5 w-3.5" />}
                          aria-label="Baixar"
                        >
                          {''}
                        </Button>
                        {execution.status !== 'SIGNED' && canSign ? (
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => setSignTarget(execution)}
                            icon={<ShieldCheck className="h-3.5 w-3.5" />}
                            aria-label="Assinar"
                          >
                            {''}
                          </Button>
                        ) : null}
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => void handleCopyVerify(execution)}
                          icon={<LinkIcon className="h-3.5 w-3.5" />}
                          aria-label="Copiar link verify"
                        >
                          {''}
                        </Button>
                        {canSign ? (
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => setLabelsTarget(execution)}
                            icon={<Tag className="h-3.5 w-3.5" />}
                            aria-label="Etiquetas"
                          >
                            {''}
                          </Button>
                        ) : null}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {page && page.totalPages > 0 ? (
          <footer className="mt-4 flex items-center justify-between text-xs text-neutral-500">
            <span>
              Pagina {page.number + 1} de {Math.max(page.totalPages, 1)} ({page.totalElements}{' '}
              execucoes)
            </span>
            <div className="flex items-center gap-2">
              <Button
                variant="secondary"
                size="sm"
                disabled={page.number <= 0}
                onClick={() =>
                  setFilter((current) => ({ ...current, page: Math.max(0, (current.page ?? 0) - 1) }))
                }
                icon={<ChevronLeft className="h-3.5 w-3.5" />}
              >
                Anterior
              </Button>
              <Button
                variant="secondary"
                size="sm"
                disabled={page.number + 1 >= page.totalPages}
                onClick={() =>
                  setFilter((current) => ({ ...current, page: (current.page ?? 0) + 1 }))
                }
                icon={<ChevronRight className="h-3.5 w-3.5" />}
              >
                Proxima
              </Button>
            </div>
          </footer>
        ) : null}
      </Card>

      {selected ? <ExecutionDrawer execution={selected} onClose={() => setSelected(null)} /> : null}
      {signTarget ? (
        <SignReportModal
          execution={signTarget}
          onClose={() => setSignTarget(null)}
          onSigned={() => {
            setSignTarget(null)
            void query.refetch()
          }}
        />
      ) : null}
      {labelsTarget ? (
        <LabelsManagerModal
          execution={labelsTarget}
          onClose={() => setLabelsTarget(null)}
          onChange={() => {
            setLabelsTarget(null)
            void query.refetch()
          }}
        />
      ) : null}
    </div>
  )
}

function renderLabels(labels: string[] | null | undefined) {
  const list = labels ?? []
  if (list.length === 0) return <span className="text-xs text-neutral-400">—</span>
  return (
    <div className="flex flex-wrap gap-1">
      {list.map((code) => {
        const meta = REPORT_LABELS.find((l) => l.code === code)
        const label = meta?.label ?? code
        const color = meta?.color ?? 'bg-neutral-100 text-neutral-600'
        return (
          <span
            key={code}
            className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${color}`}
          >
            {label}
          </span>
        )
      })}
    </div>
  )
}

function renderStatus(execution: ReportExecutionResponse) {
  if (execution.status === 'SIGNED') {
    return (
      <span className="inline-flex items-center gap-1 rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-semibold text-emerald-800">
        <ShieldCheck className="h-3 w-3" /> Assinado
      </span>
    )
  }
  if (execution.status === 'SUCCESS') {
    return (
      <span className="inline-flex items-center gap-1 rounded-full bg-blue-100 px-2 py-0.5 text-xs font-semibold text-blue-800">
        <CheckCircle2 className="h-3 w-3" /> Gerado
      </span>
    )
  }
  if (execution.status === 'WITH_WARNINGS') {
    return (
      <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-semibold text-amber-900">
        <AlertTriangle className="h-3 w-3" /> Com avisos
      </span>
    )
  }
  if (execution.status === 'FAILURE') {
    return (
      <span className="inline-flex items-center gap-1 rounded-full bg-red-100 px-2 py-0.5 text-xs font-semibold text-red-800">
        <AlertTriangle className="h-3 w-3" /> Falha
      </span>
    )
  }
  return (
    <span className="inline-flex items-center gap-1 rounded-full bg-neutral-100 px-2 py-0.5 text-xs font-semibold text-neutral-700">
      {execution.status}
    </span>
  )
}

function formatBytes(bytes: number | null): string {
  if (!bytes || bytes <= 0) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
}

function nameOfCode(code: string, definitions: ReportDefinition[]): string {
  return definitions.find((def) => def.code === code)?.name ?? code
}

interface ExecutionDrawerProps {
  execution: ReportExecutionResponse
  onClose: () => void
}

function ExecutionDrawer({ execution, onClose }: ExecutionDrawerProps) {
  const { toast } = useToast()
  return (
    <div className="fixed inset-0 z-40 flex justify-end bg-black/40" onClick={onClose}>
      <aside
        className="h-full w-full max-w-md overflow-y-auto bg-white p-6 shadow-xl"
        onClick={(event) => event.stopPropagation()}
      >
        <header className="flex items-start justify-between gap-3">
          <div>
            <h3 className="text-lg font-semibold text-neutral-900">
              {execution.reportNumber ?? 'Execucao'}
            </h3>
            <p className="text-xs text-neutral-500">ID: {execution.id}</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Fechar"
            className="rounded-full p-2 text-neutral-500 hover:bg-neutral-100"
          >
            <X className="h-4 w-4" />
          </button>
        </header>

        <dl className="mt-4 space-y-3 text-sm">
          <DrawerItem label="Status">{renderStatus(execution)}</DrawerItem>
          <DrawerItem label="Tipo">{execution.reportCode}</DrawerItem>
          <DrawerItem label="Formato">{execution.format}</DrawerItem>
          <DrawerItem label="Periodo">{execution.periodLabel ?? '-'}</DrawerItem>
          <DrawerItem label="Gerado por">{execution.username ?? '-'}</DrawerItem>
          <DrawerItem label="Gerado em">
            {new Date(execution.createdAt).toLocaleString('pt-BR')}
          </DrawerItem>
          {execution.signedAt ? (
            <DrawerItem label="Assinado em">
              {new Date(execution.signedAt).toLocaleString('pt-BR')}
            </DrawerItem>
          ) : null}
          {execution.sha256 ? (
            <DrawerItem label="SHA-256 (original)">
              <code className="break-all text-xs text-neutral-700">{execution.sha256}</code>
            </DrawerItem>
          ) : null}
          {execution.signatureHash ? (
            <DrawerItem label="SHA-256 (assinado)">
              <code className="break-all text-xs text-neutral-700">{execution.signatureHash}</code>
            </DrawerItem>
          ) : null}
          {execution.warnings?.length ? (
            <DrawerItem label="Avisos">
              <ul className="list-disc space-y-1 pl-5 text-amber-900">
                {execution.warnings.map((warning, idx) => (
                  <li key={idx}>{warning}</li>
                ))}
              </ul>
            </DrawerItem>
          ) : null}
          {execution.verifyUrl ? (
            <DrawerItem label="Link publico">
              <div className="flex items-center gap-2">
                <a
                  href={execution.verifyUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="break-all text-xs text-green-800 underline"
                >
                  {execution.verifyUrl}
                </a>
                <button
                  type="button"
                  className="rounded-full p-1 text-neutral-500 hover:bg-neutral-100"
                  aria-label="Copiar link"
                  onClick={async () => {
                    try {
                      await navigator.clipboard.writeText(execution.verifyUrl ?? '')
                      toast.success('Link copiado.')
                    } catch {
                      toast.warning('Nao foi possivel copiar.')
                    }
                  }}
                >
                  <Copy className="h-3.5 w-3.5" />
                </button>
              </div>
            </DrawerItem>
          ) : null}
        </dl>
      </aside>
    </div>
  )
}

function extractErrorMessage(error: unknown): string {
  if (error instanceof Error) return error.message
  return 'Erro interno inesperado.'
}

function DrawerItem({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wider text-neutral-500">{label}</dt>
      <dd className="mt-1 text-sm text-neutral-800">{children}</dd>
    </div>
  )
}
