import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { isAxios404, reportsV2Service } from '../services/reportsV2Service'
import type {
  ExecutionsFilter,
  GenerateReportRequest,
  PreviewRequest,
  ReportCode,
  SetReportLabelsRequest,
  SignReportRequest,
} from '../types/reportsV2'

/**
 * Hook com logica de capability detection para Reports V2.
 *
 * - 200 com array nao-vazio: V2 ligada, renderiza catalogo
 * - 200 com array vazio: V2 ligada mas role sem definitions (estado vazio)
 * - 404: flag off => retorna []; hook expoe isV2Enabled=false
 */
export function useReportCatalogV2() {
  const query = useQuery({
    queryKey: ['reports', 'v2', 'catalog'],
    queryFn: () => reportsV2Service.catalog(),
    retry: (failureCount, error) => {
      // Flag off => 404. Nao tentar de novo, nao e erro transiente.
      if (isAxios404(error)) return false
      return failureCount < 2
    },
    staleTime: 5 * 60 * 1000,
  })

  // V2 ligado quando a requisicao respondeu 2xx. O service transforma 404
  // em array vazio, entao "data nao e undefined e nao deu erro" e a
  // heuristica estavel. Caso o backend tenha respondido mas vazio (flag on,
  // sem role), mantemos isV2Enabled=true e tratamos como estado vazio na UI.
  const isV2Enabled = !query.isError && Array.isArray(query.data)

  return {
    isV2Enabled,
    definitions: query.data ?? [],
    isLoading: query.isLoading,
    error: query.error,
    refetch: query.refetch,
  }
}

export function useReportDefinitionV2(code: ReportCode | undefined) {
  return useQuery({
    queryKey: ['reports', 'v2', 'definition', code],
    queryFn: () => reportsV2Service.getDefinition(code as ReportCode),
    enabled: Boolean(code),
    staleTime: 5 * 60 * 1000,
  })
}

export function useGenerateReportV2() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (req: GenerateReportRequest) => reportsV2Service.generate(req),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['reports', 'v2', 'executions'] })
    },
  })
}

export function usePreviewReportV2() {
  return useMutation({
    mutationFn: (req: PreviewRequest) => reportsV2Service.preview(req),
  })
}

export function useSignReportV2() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...req }: { id: string } & SignReportRequest) =>
      reportsV2Service.sign(id, req),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['reports', 'v2', 'executions'] })
    },
  })
}

export function useReportExecutions(filter: ExecutionsFilter) {
  return useQuery({
    queryKey: ['reports', 'v2', 'executions', filter],
    queryFn: () => reportsV2Service.listExecutions(filter),
    staleTime: 30 * 1000,
  })
}

/**
 * Mutation para aplicar/remover rotulos de uma execucao V2. Invalida o
 * cache da listagem de execucoes para forcar re-render da tabela (e do
 * drawer se aberto) com os novos labels.
 */
export function useSetReportLabels() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...req }: { id: string } & SetReportLabelsRequest) =>
      reportsV2Service.setLabels(id, req),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['reports', 'v2', 'executions'] })
    },
  })
}

/**
 * Hook de sugestoes para o filtro {@code equipment}. Cache de 5 minutos
 * (alinhado ao {@code @Cacheable} do backend) - a lista muda raramente
 * e so cresce quando um novo equipamento e registrado em manutencao.
 */
export function useEquipmentSuggestions() {
  return useQuery({
    queryKey: ['reports', 'v2', 'suggestions', 'equipment'],
    queryFn: () => reportsV2Service.equipmentSuggestions(),
    staleTime: 5 * 60 * 1000,
    // Nao explode se o endpoint falhar - retorna [] vindo do service
    // (quando a chamada rejeitar, react-query expoe o erro mas o
    // consumidor trata sugestoes ausentes como permissivo).
    retry: 1,
  })
}

export function useVerifyReport(hash: string | undefined) {
  return useQuery({
    queryKey: ['reports', 'v2', 'verify', hash],
    queryFn: () => reportsV2Service.verify(hash as string),
    enabled: Boolean(hash),
    // /verify retorna 200 mesmo em hash desconhecido (valid=false),
    // entao reenfileirar falha apenas em erros transientes.
    retry: 1,
    staleTime: 60 * 1000,
  })
}
