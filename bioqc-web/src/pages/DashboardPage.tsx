import {
  Activity,
  AlertTriangle,
  Beaker,
  CheckCircle2,
  ChevronRight,
  Clock,
  FileText,
  Package,
  TrendingUp,
} from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { useDashboardAlerts, useDashboardKpis, useRecentRecords } from '../hooks/useDashboard'
import { Card, EmptyState, Skeleton, StatCard, StatusBadge } from '../components/ui'
import { formatLongBR } from '../utils/date'

export function DashboardPage() {
  const navigate = useNavigate()
  const { data: kpis, isLoading: loadingKpis } = useDashboardKpis()
  const { data: alerts, isLoading: loadingAlerts } = useDashboardAlerts()
  const { data: records, isLoading: loadingRecords } = useRecentRecords()

  return (
    <div className="mx-auto max-w-7xl space-y-8 px-4 py-8 sm:px-6 lg:px-8">
      <Card className="overflow-hidden bg-gradient-to-r from-green-900 via-green-800 to-green-700 text-white">
        <div className="flex flex-col gap-6 md:flex-row md:items-end md:justify-between">
          <div>
            <p className="text-base font-medium uppercase tracking-[0.2em] text-green-100">Taxa de Aprovação</p>
            <div className="mt-3 text-5xl font-bold">
              {loadingKpis ? '...' : `${Math.round(kpis?.approvalRate ?? 0)}%`}
            </div>
            <p className="mt-2 text-base text-green-100">Mês atual</p>
          </div>
          <div className="rounded-2xl bg-white/10 px-4 py-3 backdrop-blur">
            <div className="text-base text-green-100">Tendência do período</div>
            <div className="mt-1 flex items-center gap-2 text-lg font-semibold">
              <TrendingUp className="h-5 w-5" />
              Estável para operação
            </div>
          </div>
        </div>
      </Card>

      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {loadingKpis ? (
          Array.from({ length: 4 }).map((_, index) => <Skeleton key={index} height="10rem" />)
        ) : (
          <>
            <StatCard icon={<Activity className="h-5 w-5" />} iconColor="bg-blue-600" value={kpis?.totalToday ?? 0} label="CQ Hoje" />
            <StatCard icon={<Beaker className="h-5 w-5" />} iconColor="bg-green-700" value={kpis?.totalMonth ?? 0} label="CQ Mês" />
            <StatCard icon={<TrendingUp className="h-5 w-5" />} iconColor="bg-emerald-600" value={`${Math.round(kpis?.approvalRate ?? 0)}%`} label="Taxa de Aprovação" />
            <StatCard icon={<AlertTriangle className="h-5 w-5" />} iconColor="bg-orange-500" value={kpis?.alertsCount ?? 0} label="Alertas Ativos" />
          </>
        )}
      </section>

      <section className="grid gap-6 xl:grid-cols-[0.95fr_1.05fr]">
        <Card className="animate-fadeIn">
          <div className="mb-5 flex items-center justify-between">
            <div>
              <h3 className="text-xl font-semibold text-neutral-900">Alertas Ativos</h3>
              <p className="text-base text-neutral-500">Visão consolidada da operação</p>
            </div>
          </div>

          {loadingAlerts ? (
            <Skeleton type="line" lines={4} />
          ) : alerts && alerts.expiringReagents.count + alerts.pendingMaintenances.count + alerts.westgardViolations.count > 0 ? (
            <div className="space-y-3">
              {alerts.expiringReagents.count > 0 ? (
                <div className="flex items-start gap-3 rounded-2xl bg-amber-50 p-4">
                  <Package className="mt-1 h-5 w-5 text-amber-700" />
                  <div>
                    <div className="text-base font-medium text-amber-900">
                      {alerts.expiringReagents.count} lotes vencem nos próximos 30 dias
                    </div>
                    <div className="text-base text-amber-700">
                      Revise o estoque para evitar ruptura.
                    </div>
                  </div>
                </div>
              ) : null}
              {alerts.pendingMaintenances.count > 0 ? (
                <div className="flex items-start gap-3 rounded-2xl bg-red-50 p-4">
                  <Clock className="mt-1 h-5 w-5 text-red-700" />
                  <div>
                    <div className="text-base font-medium text-red-900">
                      {alerts.pendingMaintenances.count} manutenções pendentes
                    </div>
                    <div className="text-base text-red-700">Equipamentos com revisão vencida.</div>
                  </div>
                </div>
              ) : null}
              {alerts.westgardViolations.count > 0 ? (
                <div className="flex items-start gap-3 rounded-2xl bg-red-50 p-4">
                  <AlertTriangle className="mt-1 h-5 w-5 text-red-700" />
                  <div>
                    <div className="text-base font-medium text-red-900">
                      {alerts.westgardViolations.count} registros com violações este mês
                    </div>
                    <div className="text-base text-red-700">Analise antes de liberar a rotina.</div>
                  </div>
                </div>
              ) : null}
            </div>
          ) : (
            <EmptyState
              icon={<CheckCircle2 className="h-8 w-8" />}
              title="Tudo em ordem!"
              description="Não há alertas ativos no momento."
            />
          )}
        </Card>

        <Card className="animate-fadeIn">
          <div className="mb-5 flex items-center justify-between">
            <div>
              <h3 className="text-xl font-semibold text-neutral-900">Registros Recentes</h3>
              <p className="text-base text-neutral-500">Últimos 10 lançamentos</p>
            </div>
            <button
              type="button"
              className="inline-flex items-center gap-1 text-base font-medium text-green-800"
              onClick={() => navigate('/qc?tab=registro')}
            >
              Ver todos <ChevronRight className="h-4 w-4" />
            </button>
          </div>

          {loadingRecords ? (
            <Skeleton type="line" lines={6} />
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full text-left">
                <thead className="text-neutral-500">
                  <tr>
                    <th className="pb-3 text-base font-medium">Data</th>
                    <th className="pb-3 text-base font-medium">Exame</th>
                    <th className="pb-3 text-base font-medium">Valor</th>
                    <th className="pb-3 text-base font-medium">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-neutral-100">
                  {records?.map((record) => (
                    <tr key={record.id}>
                      <td className="py-3 text-base">{formatLongBR(record.date)}</td>
                      <td className="py-3 text-base font-medium text-neutral-900">{record.examName}</td>
                      <td className="py-3 text-base font-mono">{record.value.toFixed(2)}</td>
                      <td className="py-3"><StatusBadge status={record.status} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      </section>

      {/* === ACOES RAPIDAS — cards grandes e claros === */}
      <section className="grid gap-4 md:grid-cols-3">
        <Card
          onClick={() => navigate('/qc?tab=registro')}
          className="animate-fadeIn border-l-4 border-green-600 bg-green-50 hover:shadow-elevated transition-shadow cursor-pointer"
        >
          <div className="flex items-center gap-4">
            <div className="rounded-full bg-green-700 p-4 text-white">
              <Beaker className="h-7 w-7" />
            </div>
            <div>
              <h3 className="text-xl font-semibold text-neutral-900">Registrar CQ</h3>
              <p className="mt-1 text-base text-neutral-600">Lançamento diário de controle de qualidade</p>
            </div>
          </div>
        </Card>

        <Card
          onClick={() => navigate('/qc?tab=configuracao')}
          className="animate-fadeIn hover:shadow-elevated transition-shadow cursor-pointer"
        >
          <div className="flex items-center gap-4">
            <div className="rounded-full bg-blue-600 p-4 text-white">
              <Package className="h-7 w-7" />
            </div>
            <div>
              <h3 className="text-xl font-semibold text-neutral-900">Configuração</h3>
              <p className="mt-1 text-base text-neutral-600">Referências, reagentes e manutenção</p>
            </div>
          </div>
        </Card>

        <Card
          onClick={() => navigate('/qc?tab=relatorios')}
          className="animate-fadeIn hover:shadow-elevated transition-shadow cursor-pointer"
        >
          <div className="flex items-center gap-4">
            <div className="rounded-full bg-amber-600 p-4 text-white">
              <FileText className="h-7 w-7" />
            </div>
            <div>
              <h3 className="text-xl font-semibold text-neutral-900">Relatórios</h3>
              <p className="mt-1 text-base text-neutral-600">Gerar PDF e importar planilhas</p>
            </div>
          </div>
        </Card>
      </section>
    </div>
  )
}
