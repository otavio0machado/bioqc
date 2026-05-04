import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { hematologyService } from '../services/hematologyService'
import type {
  HematologyBioRequest,
  HematologyMeasurementRequest,
  HematologyParameterRequest,
} from '../types'

export function useHematologyParameters(analito?: string) {
  return useQuery({
    queryKey: ['hematology', 'parameters', analito],
    queryFn: () => hematologyService.getParameters(analito),
  })
}

export function useCreateHematologyParameter() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: HematologyParameterRequest) => hematologyService.createParameter(request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['hematology', 'parameters'] })
    },
  })
}

export function useHematologyMeasurements(parameterId?: string) {
  return useQuery({
    queryKey: ['hematology', 'measurements', parameterId],
    queryFn: () => hematologyService.getMeasurements(parameterId),
  })
}

export function useCreateHematologyMeasurement() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: HematologyMeasurementRequest) => hematologyService.createMeasurement(request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['hematology', 'measurements'] })
    },
  })
}

export function useHematologyBioRecords() {
  return useQuery({
    queryKey: ['hematology', 'bio-records'],
    queryFn: () => hematologyService.getBioRecords(),
  })
}

export function useCreateHematologyBioRecord() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: HematologyBioRequest) => hematologyService.createBioRecord(request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['hematology', 'bio-records'] })
    },
  })
}
