import { useQuery } from '@tanstack/react-query'
import { reportService } from '../services/reportService'
import { qcService } from '../services/qcService'

/** Historico de relatorios gerados (RelatoriosTab V2). */
export function useReportHistory(limit = 20) {
  return useQuery({
    queryKey: ['report-history', limit],
    queryFn: () => reportService.history(limit),
  })
}

/** Historico de importacoes em lote (ImportarTab V2). */
export function useImportHistory(limit = 20) {
  return useQuery({
    queryKey: ['import-history', limit],
    queryFn: () => qcService.getImportHistory(limit),
  })
}
