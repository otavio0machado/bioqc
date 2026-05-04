import { useMutation } from '@tanstack/react-query'
import { aiService } from '../services/aiService'
import type { AiAnalysisRequest, VoiceToFormRequest } from '../types'

export function useAiAnalysis() {
  return useMutation({
    mutationFn: (request: AiAnalysisRequest) => aiService.analyze(request),
  })
}

export function useVoiceToForm() {
  return useMutation({
    mutationFn: (request: VoiceToFormRequest) => aiService.voiceToForm(request),
  })
}
