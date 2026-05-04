import { api } from './api'
import type { ReportRun } from '../types'

export const reportService = {
  async getQcPdf(filters?: { area?: string; periodType?: string; month?: string; year?: string }) {
    const response = await api.get<Blob>('/reports/qc-pdf', {
      params: filters,
      responseType: 'blob',
    })
    return response.data
  },
  async getReagentsPdf() {
    const response = await api.get<Blob>('/reports/reagents-pdf', {
      responseType: 'blob',
    })
    return response.data
  },
  async history(limit = 20): Promise<ReportRun[]> {
    const response = await api.get<ReportRun[]>('/reports/history', { params: { limit } })
    return response.data
  },
}
