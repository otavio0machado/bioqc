import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { AdminResetPasswordRequest, AdminUpdateUserRequest, AdminUserRequest } from '../types'
import { adminService } from '../services/adminService'
import { auditService } from '../services/auditService'

export function useUsers() {
  return useQuery({
    queryKey: ['admin', 'users'],
    queryFn: adminService.getUsers,
  })
}

export function useCreateUser() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: AdminUserRequest) => adminService.createUser(request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'users'] }),
  })
}

export function useUpdateUser() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: AdminUpdateUserRequest }) =>
      adminService.updateUser(id, request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'users'] }),
  })
}

export function useResetPassword() {
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: AdminResetPasswordRequest }) =>
      adminService.resetPassword(id, request),
  })
}

export function useAuditLogs(userId?: string) {
  return useQuery({
    queryKey: ['admin', 'audit-logs', userId ?? 'all'],
    queryFn: () => auditService.getLogs(userId, 200),
    refetchInterval: 30000,
  })
}
