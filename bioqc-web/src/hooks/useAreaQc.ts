import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { areaQcService } from '../services/areaQcService'
import type { AreaQcMeasurementRequest, AreaQcParameterRequest } from '../types'

export function useAreaQcParameters(area: string, analito?: string) {
  return useQuery({
    queryKey: ['area-qc', area, 'parameters', analito],
    queryFn: () => areaQcService.getParameters(area, analito),
  })
}

export function useCreateAreaQcParameter(area: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: AreaQcParameterRequest) => areaQcService.createParameter(area, request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['area-qc', area, 'parameters'] })
    },
  })
}

export function useUpdateAreaQcParameter(area: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: AreaQcParameterRequest }) =>
      areaQcService.updateParameter(area, id, request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['area-qc', area, 'parameters'] })
    },
  })
}

export function useDeleteAreaQcParameter(area: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => areaQcService.deleteParameter(area, id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['area-qc', area, 'parameters'] })
    },
  })
}

export function useAreaQcMeasurements(
  area: string,
  filters?: { analito?: string; startDate?: string; endDate?: string },
) {
  return useQuery({
    queryKey: ['area-qc', area, 'measurements', filters],
    queryFn: () => areaQcService.getMeasurements(area, filters),
  })
}

export function useCreateAreaQcMeasurement(area: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: AreaQcMeasurementRequest) => areaQcService.createMeasurement(area, request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['area-qc', area, 'measurements'] })
    },
  })
}
