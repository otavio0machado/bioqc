import { api } from './api'
import type { MaintenanceRecord, MaintenanceRequest } from '../types'

export const maintenanceService = {
  async getRecords(equipment?: string) {
    const response = await api.get<MaintenanceRecord[]>('/maintenance', { params: { equipment } })
    return response.data
  },
  async createRecord(request: MaintenanceRequest) {
    const response = await api.post<MaintenanceRecord>('/maintenance', request)
    return response.data
  },
  async updateRecord(id: string, request: MaintenanceRequest) {
    const response = await api.put<MaintenanceRecord>(`/maintenance/${id}`, request)
    return response.data
  },
  async deleteRecord(id: string) {
    await api.delete(`/maintenance/${id}`)
  },
  async getPending() {
    const response = await api.get<MaintenanceRecord[]>('/maintenance/pending')
    return response.data
  },
}
