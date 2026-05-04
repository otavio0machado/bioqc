import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { maintenanceService } from '../services/maintenanceService'
import type { MaintenanceRequest } from '../types'

export function useMaintenanceRecords(equipment?: string) {
  return useQuery({
    queryKey: ['maintenance', equipment],
    queryFn: () => maintenanceService.getRecords(equipment),
  })
}

export function useCreateMaintenanceRecord() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: MaintenanceRequest) => maintenanceService.createRecord(request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['maintenance'] })
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
  })
}

export function useUpdateMaintenanceRecord() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: MaintenanceRequest }) =>
      maintenanceService.updateRecord(id, request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['maintenance'] })
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
  })
}

export function useDeleteMaintenanceRecord() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => maintenanceService.deleteRecord(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['maintenance'] })
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
  })
}
