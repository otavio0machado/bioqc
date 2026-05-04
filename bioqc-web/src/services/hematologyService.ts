import { api } from './api'
import type {
  HematologyBioRecord,
  HematologyBioRequest,
  HematologyMeasurementRequest,
  HematologyParameterRequest,
  HematologyQcMeasurement,
  HematologyQcParameter,
} from '../types'

export const hematologyService = {
  async getParameters(analito?: string) {
    const response = await api.get<HematologyQcParameter[]>('/hematology/parameters', { params: { analito } })
    return response.data
  },
  async createParameter(request: HematologyParameterRequest) {
    const response = await api.post<HematologyQcParameter>('/hematology/parameters', request)
    return response.data
  },
  async getMeasurements(parameterId?: string) {
    const response = await api.get<HematologyQcMeasurement[]>('/hematology/measurements', { params: { parameterId } })
    return response.data
  },
  async createMeasurement(request: HematologyMeasurementRequest) {
    const response = await api.post<HematologyQcMeasurement>('/hematology/measurements', request)
    return response.data
  },
  async getBioRecords() {
    const response = await api.get<HematologyBioRecord[]>('/hematology/bio-records')
    return response.data
  },
  async createBioRecord(request: HematologyBioRequest) {
    const response = await api.post<HematologyBioRecord>('/hematology/bio-records', request)
    return response.data
  },
}
