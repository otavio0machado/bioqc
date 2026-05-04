import axios from 'axios'
import { Tag } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useSetReportLabels } from '../../hooks/useReportsV2'
import type { ReportExecutionResponse } from '../../types/reportsV2'
import { Button, Modal, useToast } from '../ui'
import { REPORT_LABELS } from './reportLabels'

interface LabelsManagerModalProps {
  execution: ReportExecutionResponse
  onClose: () => void
  onChange: (updated: ReportExecutionResponse) => void
}

/**
 * Modal para editar os rotulos de uma execucao V2.
 *
 * <p>Mostra as 4 labels conhecidas (`REPORT_LABELS`) como checkboxes,
 * pre-marcadas de acordo com {@code execution.labels}. Ao salvar, calcula
 * o diff entre o estado inicial e o atual e envia apenas {@code add} e
 * {@code remove} para {@code POST /api/reports/v2/executions/{id}/labels}.
 */
export function LabelsManagerModal({ execution, onClose, onChange }: LabelsManagerModalProps) {
  const { toast } = useToast()
  const setLabels = useSetReportLabels()

  // Guardamos uma copia do estado original para calcular o diff ao salvar.
  const initialSet = useMemo(() => new Set(execution.labels ?? []), [execution.labels])
  const [selected, setSelected] = useState<Set<string>>(() => new Set(initialSet))
  const [error, setError] = useState<string | null>(null)

  const toggle = (code: string) => {
    setSelected((current) => {
      const next = new Set(current)
      if (next.has(code)) next.delete(code)
      else next.add(code)
      return next
    })
  }

  const handleSave = async () => {
    setError(null)
    const add: string[] = []
    const remove: string[] = []
    for (const option of REPORT_LABELS) {
      const wasActive = initialSet.has(option.code)
      const isActive = selected.has(option.code)
      if (!wasActive && isActive) add.push(option.code)
      if (wasActive && !isActive) remove.push(option.code)
    }

    // Sem alteracoes - evitamos ida ao servidor.
    if (add.length === 0 && remove.length === 0) {
      onClose()
      return
    }

    try {
      const updated = await setLabels.mutateAsync({ id: execution.id, add, remove })
      onChange(updated)
      toast.success('Etiquetas atualizadas.')
      onClose()
    } catch (err) {
      setError(extractErrorMessage(err))
    }
  }

  return (
    <Modal
      isOpen
      onClose={onClose}
      title={`Etiquetas ${execution.reportNumber ?? ''}`.trim()}
      footer={
        <div className="flex items-center justify-end gap-3">
          <Button variant="secondary" onClick={onClose} disabled={setLabels.isPending}>
            Cancelar
          </Button>
          <Button
            onClick={() => void handleSave()}
            loading={setLabels.isPending}
            icon={<Tag className="h-4 w-4" />}
          >
            Salvar
          </Button>
        </div>
      }
    >
      <div className="space-y-3">
        <p className="text-sm text-neutral-600">
          Aplique etiquetas para organizar o historico (ex.: oficiais do mes, entregas a
          vigilancia). Voce pode combinar varias etiquetas em uma mesma execucao.
        </p>
        <ul className="space-y-2">
          {REPORT_LABELS.map((option) => {
            const isActive = selected.has(option.code)
            return (
              <li key={option.code}>
                <label className="flex cursor-pointer items-center gap-3 rounded-xl border border-neutral-200 bg-white px-4 py-3 transition hover:bg-neutral-50">
                  <input
                    type="checkbox"
                    checked={isActive}
                    onChange={() => toggle(option.code)}
                    className="h-4 w-4 rounded border-neutral-300 text-green-800 focus:ring-green-800"
                    aria-label={option.label}
                  />
                  <span className="flex-1 text-sm font-medium text-neutral-800">
                    {option.label}
                  </span>
                  <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${option.color}`}>
                    {option.code}
                  </span>
                </label>
              </li>
            )
          })}
        </ul>

        {error ? (
          <div className="rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-800">
            {error}
          </div>
        ) : null}
      </div>
    </Modal>
  )
}

function extractErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as
      | { message?: string; error?: string; detail?: string; title?: string }
      | undefined
    return data?.detail ?? data?.message ?? data?.title ?? data?.error ?? error.message
  }
  if (error instanceof Error) return error.message
  return 'Erro desconhecido'
}
