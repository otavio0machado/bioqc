import axios, { AxiosError, type AxiosResponse } from 'axios'
import { api } from './api'
import type {
  ExecutionsFilter,
  GenerateReportRequest,
  PageResponse,
  PreviewRequest,
  PreviewResponse,
  ReportCode,
  ReportDefinition,
  ReportDefinitionRawResponse,
  ReportExecutionResponse,
  SetReportLabelsRequest,
  SignReportRequest,
  VerifyReportResponse,
} from '../types/reportsV2'

/**
 * Normaliza a resposta crua da definicao (com {@code filters: []}) para a
 * forma aninhada usada na UI ({@code filterSpec.fields: []}). Os demais
 * campos sao encaminhados sem mudanca.
 */
function normalizeDefinition(raw: ReportDefinitionRawResponse): ReportDefinition {
  return {
    code: raw.code,
    name: raw.name,
    description: raw.description,
    // subtitle/icon sao opcionais no contrato atual - se o backend estiver
    // numa versao anterior ao Reports V2 expandido, caem para string vazia
    // e o catalogo usa defaults ao renderizar.
    subtitle: raw.subtitle ?? '',
    icon: raw.icon ?? '',
    category: raw.category,
    supportedFormats: raw.supportedFormats as ReportDefinition['supportedFormats'],
    filterSpec: { fields: raw.filters ?? [] },
    roleAccess: raw.roleAccess ?? [],
    signatureRequired: raw.signatureRequired,
    previewSupported: raw.previewSupported,
    // aiCommentaryCapable pode faltar em backends antigos; assumimos false
    // para nao expor um toggle que o backend nao respeita.
    aiCommentaryCapable: raw.aiCommentaryCapable ?? false,
    retentionDays: raw.retentionDays,
    legalBasis: raw.legalBasis,
  }
}

function normalizeExecution(raw: ReportExecutionResponse): ReportExecutionResponse {
  return {
    ...raw,
    labels: raw.labels ?? [],
    warnings: raw.warnings ?? [],
  }
}

/**
 * Util para detectar especificamente 404 (feature flag off em
 * {@code /catalog}). Evita erros de rede/500 serem tratados como "flag off".
 */
export function isAxios404(error: unknown): boolean {
  if (!axios.isAxiosError(error)) return false
  return (error as AxiosError).response?.status === 404
}

export interface DownloadBlobResult {
  blob: Blob
  filename: string | null
  reportNumber: string | null
  hash: string | null
  originalHash: string | null
}

export const reportsV2Service = {
  async catalog(): Promise<ReportDefinition[]> {
    try {
      const { data } = await api.get<ReportDefinitionRawResponse[]>('/reports/v2/catalog')
      return (data ?? []).map(normalizeDefinition)
    } catch (error) {
      // Flag off no backend => controller nao registrado => 404.
      // Tratamos como "V2 indisponivel" retornando lista vazia.
      if (isAxios404(error)) {
        return []
      }
      throw error
    }
  },

  async getDefinition(code: ReportCode): Promise<ReportDefinition> {
    const { data } = await api.get<ReportDefinitionRawResponse>(`/reports/v2/catalog/${code}`)
    return normalizeDefinition(data)
  },

  async generate(req: GenerateReportRequest): Promise<ReportExecutionResponse> {
    const { data } = await api.post<ReportExecutionResponse>('/reports/v2/generate', {
      code: req.code,
      format: req.format ?? 'PDF',
      filters: req.filters ?? {},
    })
    return normalizeExecution(data)
  },

  async preview(req: PreviewRequest): Promise<PreviewResponse> {
    const { data } = await api.post<PreviewResponse>('/reports/v2/preview', {
      code: req.code,
      filters: req.filters ?? {},
    })
    return data
  },

  async sign(id: string, req: SignReportRequest): Promise<ReportExecutionResponse> {
    const payload = {
      signerName: req.signerName ?? null,
      signerRegistration: req.signerRegistration ?? null,
    }
    const { data } = await api.post<ReportExecutionResponse>(
      `/reports/v2/executions/${id}/sign`,
      payload,
    )
    return normalizeExecution(data)
  },

  async verify(hash: string): Promise<VerifyReportResponse> {
    // /verify e publico - nao depende do token. Usamos a mesma instancia
    // para aproveitar baseURL/retry, mas o endpoint ignora Authorization.
    const { data } = await api.get<VerifyReportResponse>(
      `/reports/v2/verify/${encodeURIComponent(hash)}`,
    )
    return data
  },

  async listExecutions(filter: ExecutionsFilter): Promise<PageResponse<ReportExecutionResponse>> {
    const params: Record<string, string | number> = {}
    if (filter.code) params.code = filter.code
    if (filter.status) params.status = filter.status
    if (filter.from) params.from = filter.from
    if (filter.to) params.to = filter.to
    if (typeof filter.page === 'number') params.page = filter.page
    if (typeof filter.size === 'number') params.size = filter.size

    const { data } = await api.get<PageResponse<ReportExecutionResponse>>('/reports/v2/executions', {
      params,
    })
    return {
      ...data,
      content: (data.content ?? []).map(normalizeExecution),
    }
  },

  async getExecution(id: string): Promise<ReportExecutionResponse> {
    const { data } = await api.get<ReportExecutionResponse>(`/reports/v2/executions/${id}`)
    return normalizeExecution(data)
  },

  /**
   * Aplica rotulos (add) e/ou remove (remove) em uma execucao V2. Backend
   * aplica adicoes antes das remocoes e devolve a execucao atualizada.
   */
  async setLabels(id: string, req: SetReportLabelsRequest): Promise<ReportExecutionResponse> {
    const payload: SetReportLabelsRequest = {
      add: req.add ?? [],
      remove: req.remove ?? [],
    }
    const { data } = await api.post<ReportExecutionResponse>(
      `/reports/v2/executions/${id}/labels`,
      payload,
    )
    return normalizeExecution(data)
  },

  /**
   * Sugestoes de autocomplete para o filtro {@code equipment}. O backend
   * envolve a lista em {@code { items: string[] }} e faz cache de 5 min
   * (que replicamos na camada de hooks).
   */
  async equipmentSuggestions(): Promise<string[]> {
    const { data } = await api.get<{ items: string[] }>('/reports/v2/suggestions/equipment')
    return data?.items ?? []
  },

  /**
   * Baixa o PDF da execucao. Retorna o blob junto com os headers
   * {@code X-Report-Number}, {@code X-Report-Hash} e
   * {@code X-Report-Original-Hash} para uso da UI (por ex. exibicao do
   * numero do laudo no toast de sucesso).
   */
  async downloadBlob(id: string): Promise<DownloadBlobResult> {
    const response: AxiosResponse<Blob> = await api.get(`/reports/v2/executions/${id}/download`, {
      responseType: 'blob',
    })
    const headers = response.headers
    const disposition = (headers['content-disposition'] ?? headers['Content-Disposition']) as
      | string
      | undefined
    const filename = extractFilename(disposition)
    return {
      blob: response.data,
      filename,
      reportNumber: (headers['x-report-number'] ?? headers['X-Report-Number'] ?? null) as
        | string
        | null,
      hash: (headers['x-report-hash'] ?? headers['X-Report-Hash'] ?? null) as string | null,
      originalHash: (headers['x-report-original-hash'] ?? headers['X-Report-Original-Hash'] ?? null) as
        | string
        | null,
    }
  },
}

function extractFilename(disposition: string | undefined): string | null {
  if (!disposition) return null
  // Formato: attachment; filename="BIO-202604-000001.pdf"
  const match = /filename\s*=\s*"?([^";]+)"?/i.exec(disposition)
  return match ? match[1].trim() : null
}
