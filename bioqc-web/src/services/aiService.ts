import { api } from './api'
import type { AiAnalysisRequest, VoiceFormData, VoiceToFormRequest } from '../types'

export const aiService = {
  async analyze(request: AiAnalysisRequest) {
    const response = await api.post<{ response: string }>('/ai/analyze', request)
    return response.data.response
  },
  async voiceToForm(request: VoiceToFormRequest) {
    const response = await api.post<VoiceFormData>('/ai/voice-to-form', request)
    return response.data
  },
}
