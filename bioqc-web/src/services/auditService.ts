import { api } from './api'

export interface AuditLogEntry {
  id: string
  action: string
  entityType: string
  entityId: string | null
  details: Record<string, unknown> | null
  createdAt: string
  userName: string | null
  username: string | null
}

export const auditService = {
  async getLogs(userId?: string, limit = 100): Promise<AuditLogEntry[]> {
    const params: Record<string, string | number> = { limit }
    if (userId) params.userId = userId
    const { data } = await api.get<AuditLogEntry[]>('/admin/audit-logs', { params })
    return data
  },
}
