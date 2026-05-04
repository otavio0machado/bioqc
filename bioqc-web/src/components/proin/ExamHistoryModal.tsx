import { Activity } from 'lucide-react'
import { lazy, Suspense, useMemo } from 'react'
import { useQcRecords } from '../../hooks/useQcRecords'
import type { QcRecord } from '../../types'
import { Card, EmptyState, Modal, Skeleton, StatusBadge } from '../ui'
import { compareLocalDate, formatLongBR } from '../../utils/date'

const LeveyJenningsChart = lazy(() =>
  import('../charts/LeveyJenningsChart').then((module) => ({ default: module.LeveyJenningsChart })),
)

interface ExamHistoryModalProps {
  area: string
  examName: string | null
  level?: string | null
  onClose: () => void
}

/**
 * Historico completo de um exame de CQ.
 *
 * Aberto ao clicar no nome do exame na lista de registros. Exibe:
 *  - Levey-Jennings (reusa o componente de grafico canonico)
 *  - Tabela cronologica completa de registros (mais recentes primeiro)
 *
 * Usa o filtro examName do endpoint /qc-records e ordena client-side para
 * garantir ordem desc por data independente do backend.
 */
export function ExamHistoryModal({ area, examName, level, onClose }: ExamHistoryModalProps) {
  const isOpen = examName !== null

  const { data: records = [], isLoading } = useQcRecords(
    isOpen ? { area, examName: examName ?? undefined } : undefined,
  )

  const sorted = useMemo<QcRecord[]>(() => {
    const list = [...records]
    list.sort((a, b) => compareLocalDate(b.date, a.date))
    return list
  }, [records])

  // Nivel inicial do grafico: o nivel que veio pelo clique, ou o mais recente
  const defaultLevel = useMemo(() => {
    if (level) return level
    return sorted[0]?.level ?? 'Normal'
  }, [level, sorted])

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={examName ? `Histórico — ${examName}` : ''}
      size="lg"
    >
      {!isOpen ? null : isLoading ? (
        <Skeleton height="24rem" />
      ) : (
        <div className="space-y-6">
          <Suspense fallback={<Skeleton height="20rem" />}>
            <LeveyJenningsChart examName={examName!} level={defaultLevel} area={area} />
          </Suspense>

          <Card className="space-y-3">
            <div className="flex items-center justify-between">
              <div>
                <h4 className="text-base font-semibold text-neutral-900">
                  Todos os registros ({sorted.length})
                </h4>
                <p className="text-sm text-neutral-500">Ordem cronológica — mais recentes primeiro.</p>
              </div>
            </div>
            {sorted.length === 0 ? (
              <EmptyState
                icon={<Activity className="h-8 w-8" />}
                title="Sem registros"
                description={`Ainda não há registros para ${examName}.`}
              />
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-sm">
                  <thead>
                    <tr className="border-b border-neutral-100 text-xs uppercase tracking-wider text-neutral-500">
                      <th className="px-3 py-2.5">Data</th>
                      <th className="px-3 py-2.5">Nível</th>
                      <th className="px-3 py-2.5">Valor</th>
                      <th className="px-3 py-2.5">Alvo</th>
                      <th className="px-3 py-2.5">DP</th>
                      <th className="px-3 py-2.5">Z-Score</th>
                      <th className="px-3 py-2.5">Status</th>
                      <th className="px-3 py-2.5">Lote</th>
                      <th className="px-3 py-2.5">Analista</th>
                    </tr>
                  </thead>
                  <tbody>
                    {sorted.map((record) => (
                      <tr key={record.id} className="border-b border-neutral-50 hover:bg-neutral-50/50">
                        <td className="whitespace-nowrap px-3 py-2 text-neutral-700">
                          {formatLongBR(record.date)}
                        </td>
                        <td className="px-3 py-2 text-neutral-700">{record.level}</td>
                        <td className="px-3 py-2 font-mono text-neutral-900">{record.value.toFixed(2)}</td>
                        <td className="px-3 py-2 font-mono text-neutral-600">{record.targetValue?.toFixed(2) ?? '—'}</td>
                        <td className="px-3 py-2 font-mono text-neutral-600">{record.targetSd?.toFixed(2) ?? '—'}</td>
                        <td className="px-3 py-2 font-mono text-neutral-600">{record.zScore?.toFixed(2) ?? '—'}</td>
                        <td className="px-3 py-2"><StatusBadge status={record.status} /></td>
                        <td className="px-3 py-2 text-neutral-500">{record.lotNumber || '—'}</td>
                        <td className="px-3 py-2 text-neutral-500">{record.analyst || '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>
        </div>
      )}
    </Modal>
  )
}
