import { api } from './api'
import type {
  BatchImportResult,
  ImportRun,
  LeveyJenningsPoint,
  PostCalibrationRecord,
  PostCalibrationRequest,
  QcExam,
  QcExamRequest,
  QcRecord,
  QcRecordRequest,
  QcReferenceRequest,
  QcReferenceValue,
} from '../types'

export const qcService = {
  async getRecords(filters?: { area?: string; examName?: string; startDate?: string; endDate?: string }) {
    const response = await api.get<QcRecord[]>('/qc/records', { params: filters })
    return response.data
  },
  async createRecord(request: QcRecordRequest) {
    const response = await api.post<QcRecord>('/qc/records', request)
    return response.data
  },
  async createBatch(requests: QcRecordRequest[]) {
    const response = await api.post<QcRecord[]>('/qc/records/batch', requests)
    return response.data
  },
  /**
   * Importacao V2 com resposta linha-a-linha e auditoria ImportRun.
   * mode=partial (default) nao aborta o lote quando ha falhas isoladas.
   */
  async createBatchV2(requests: QcRecordRequest[], mode: 'partial' | 'atomic' = 'partial') {
    const response = await api.post<BatchImportResult>(
      '/qc/records/batch-v2',
      requests,
      { params: { mode } },
    )
    return response.data
  },
  async getImportHistory(limit = 20): Promise<ImportRun[]> {
    const response = await api.get<ImportRun[]>('/qc/records/import-history', { params: { limit } })
    return response.data
  },
  async getStatistics() {
    const response = await api.get<{ totalToday: number; totalMonth: number; approvalRate: number }>('/qc/records/statistics')
    return response.data
  },
  async getLeveyJenningsData(examName: string, level: string, area: string, days?: number) {
    const response = await api.get<LeveyJenningsPoint[]>('/qc/records/levey-jennings', {
      params: { examName, level, area, days },
    })
    return response.data
  },
  async createPostCalibration(id: string, request: PostCalibrationRequest) {
    const response = await api.post<PostCalibrationRecord>(`/qc/records/${id}/post-calibration`, request)
    return response.data
  },
  async getExams(area?: string) {
    const response = await api.get<QcExam[]>('/qc/exams', { params: { area } })
    return response.data
  },
  async createExam(request: QcExamRequest) {
    const response = await api.post<QcExam>('/qc/exams', request)
    return response.data
  },
  async getReferences(examId?: string, activeOnly = false) {
    const response = await api.get<QcReferenceValue[]>('/qc/references', {
      params: { examId, activeOnly },
    })
    return response.data
  },
  async createReference(request: QcReferenceRequest) {
    const response = await api.post<QcReferenceValue>('/qc/references', request)
    return response.data
  },
  async updateReference(id: string, request: QcReferenceRequest) {
    const response = await api.put<QcReferenceValue>(`/qc/references/${id}`, request)
    return response.data
  },
  async deleteReference(id: string) {
    await api.delete(`/qc/references/${id}`)
  },
  async updateRecord(id: string, request: QcRecordRequest) {
    const response = await api.put<QcRecord>(`/qc/records/${id}`, request)
    return response.data
  },
  async deleteRecord(id: string) {
    await api.delete(`/qc/records/${id}`)
  },
  async getLastReference(examId: string, level: string) {
    const { data } = await api.get<QcReferenceValue>('/qc/references/last', {
      params: { examId, level },
    })
    return data
  },
}
