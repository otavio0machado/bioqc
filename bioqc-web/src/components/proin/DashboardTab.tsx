import { AlertTriangle, Beaker, CheckCircle2, TrendingUp } from 'lucide-react'
import { useDashboardKpis } from '../../hooks/useDashboard'
import { useQcRecords } from '../../hooks/useQcRecords'
import { Button, Card, EmptyState, Skeleton, StatCard } from '../ui'
import { formatLongBR } from '../../utils/date'

interface DashboardTabProps {
  area: string
}

export function DashboardTab({ area }: DashboardTabProps) {
  const { data: kpis, isLoading: kpisLoading, refetch: refetchKpis } = useDashboardKpis(area)
  const { data: records, isLoading: recordsLoading } = useQcRecords({ area })

  const isLoading = kpisLoading || recordsLoading

  if (isLoading) {
    return <Skeleton height="18rem" />
  }

  if (!kpis || kpis.totalMonth === 0) {
    return (
      <EmptyState
        icon={<Beaker className="h-8 w-8" />}
        title="Sem lançamentos nesta área"
        description="Os indicadores vão aparecer assim que os primeiros registros forem adicionados."
      />
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-lg font-semibold text-neutral-900">Dashboard CQ</h3>
          <p className="text-sm text-neutral-500">Resumo operacional da área de {area}</p>
        </div>
        <Button variant="secondary" onClick={() => void refetchKpis()}>
          Atualizar dados
        </Button>
      </div>

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <StatCard icon={<Beaker className="h-5 w-5" />} iconColor="bg-blue-600" value={kpis.totalToday} label="CQ Hoje" />
        <StatCard icon={<TrendingUp className="h-5 w-5" />} iconColor="bg-green-700" value={kpis.totalMonth} label="CQ Mês" />
        <StatCard icon={<CheckCircle2 className="h-5 w-5" />} iconColor="bg-emerald-600" value={`${Math.round(kpis.approvalRate)}%`} label="Taxa Aprovação" />
        <StatCard icon={<AlertTriangle className="h-5 w-5" />} iconColor="bg-orange-500" value={kpis.alertsCount} label="Alertas" />
      </div>

      {records && records.length > 0 && (
        <Card>
          <h4 className="text-base font-semibold text-neutral-900">Últimos registros</h4>
          <div className="mt-4 space-y-3">
            {records.slice(0, 6).map((record) => (
              <div key={record.id} className="flex items-center justify-between rounded-2xl bg-neutral-50 px-4 py-3">
                <div>
                  <div className="font-medium text-neutral-900">{record.examName}</div>
                  <div className="text-sm text-neutral-500">
                    {formatLongBR(record.date)} · {record.level}
                  </div>
                </div>
                <div className="text-right">
                  <div className="font-semibold text-neutral-900">{(record.value ?? 0).toFixed(2)}</div>
                  <div className="text-sm text-neutral-500">{record.status}</div>
                </div>
              </div>
            ))}
          </div>
        </Card>
      )}
    </div>
  )
}
