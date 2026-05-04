import { useQuery } from '@tanstack/react-query'
import { dashboardService } from '../services/dashboardService'

const dashboardQueryOptions = {
  refetchInterval: 60000,
  staleTime: 30000,
} as const

export function useDashboardKpis(area?: string) {
  return useQuery({
    queryKey: ['dashboard', 'kpis', area],
    queryFn: () => dashboardService.getKpis(area),
    ...dashboardQueryOptions,
  })
}

export function useDashboardAlerts() {
  return useQuery({
    queryKey: ['dashboard', 'alerts'],
    queryFn: () => dashboardService.getAlerts(),
    ...dashboardQueryOptions,
  })
}

export function useRecentRecords(limit = 10) {
  return useQuery({
    queryKey: ['dashboard', 'recent', limit],
    queryFn: () => dashboardService.getRecentRecords(limit),
    ...dashboardQueryOptions,
  })
}
