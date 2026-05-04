import { api } from './api'
import type {
  AreaQcMeasurement,
  AreaQcMeasurementRequest,
  AreaQcParameter,
  AreaQcParameterRequest,
} from '../types'

export const areaQcService = {
  async getParameters(area: string, analito?: string) {
    const response = await api.get<AreaQcParameter[]>(`/qc/areas/${area}/parameters`, { params: { analito } })
    return response.data
  },
  async createParameter(area: string, request: AreaQcParameterRequest) {
    const response = await api.post<AreaQcParameter>(`/qc/areas/${area}/parameters`, request)
    return response.data
  },
  async updateParameter(area: string, id: string, request: AreaQcParameterRequest) {
    const response = await api.put<AreaQcParameter>(`/qc/areas/${area}/parameters/${id}`, request)
    return response.data
  },
  async deleteParameter(area: string, id: string) {
    await api.delete(`/qc/areas/${area}/parameters/${id}`)
  },
  async getMeasurements(area: string, filters?: { analito?: string; startDate?: string; endDate?: string }) {
    const response = await api.get<AreaQcMeasurement[]>(`/qc/areas/${area}/measurements`, { params: filters })
    return response.data
  },
  async createMeasurement(area: string, request: AreaQcMeasurementRequest) {
    const response = await api.post<AreaQcMeasurement>(`/qc/areas/${area}/measurements`, request)
    return response.data
  },
}
