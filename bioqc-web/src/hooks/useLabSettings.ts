import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { labSettingsService, type LabSettings } from '../services/labSettingsService'

export function useLabSettings() {
  return useQuery({
    queryKey: ['lab-settings'],
    queryFn: labSettingsService.getSettings,
  })
}

export function useUpdateLabSettings() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: LabSettings) => labSettingsService.updateSettings(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['lab-settings'] })
    },
  })
}

export function useLabReportEmails() {
  return useQuery({
    queryKey: ['lab-report-emails'],
    queryFn: labSettingsService.listEmails,
  })
}

export function useAddLabReportEmail() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: { email: string; name?: string }) => labSettingsService.addEmail(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['lab-report-emails'] })
    },
  })
}

export function useToggleLabReportEmail() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      labSettingsService.toggleEmail(id, active),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['lab-report-emails'] })
    },
  })
}

export function useRemoveLabReportEmail() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => labSettingsService.removeEmail(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['lab-report-emails'] })
    },
  })
}
