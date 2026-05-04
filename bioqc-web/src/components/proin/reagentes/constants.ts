import type { ReagentStatus } from '../../../types'

export const MOVEMENT_REASONS: { value: string; label: string }[] = [
  { value: 'CONTAGEM_FISICA', label: 'Contagem física' },
  { value: 'QUEBRA', label: 'Quebra / perda' },
  { value: 'CONTAMINACAO', label: 'Contaminação' },
  { value: 'CORRECAO', label: 'Correção de lançamento' },
  { value: 'REVERSAO_ABERTURA', label: 'Reversão de abertura por engano' },
  { value: 'VENCIMENTO', label: 'Vencimento' },
  { value: 'OUTRO', label: 'Outro (ver observação)' },
]

/**
 * Lista fechada de categorias canonicas. DEVE ficar espelhada com
 * {@code CategoryRegistry.ALL} no backend (ReagentService valida pelo conjunto).
 */
export const CATEGORIES = [
  'Bioquímica',
  'Hematologia',
  'Imunologia',
  'Parasitologia',
  'Microbiologia',
  'Uroanálise',
  'Kit Diagnóstico',
  'Controle CQ',
  'Calibrador',
  'Geral',
]

/**
 * Lista fechada de temperaturas canonicas. Espelhada com
 * {@code StorageTempRegistry.ALL} no backend.
 */
export const TEMPS = ['2-8°C', '15-25°C (Ambiente)', '-20°C', '-80°C']

/**
 * Status canonicos pos-refator v3. Drop {@code fora_de_estoque}, add
 * {@code inativo} (terminal manual via {@code POST /archive}).
 *
 * Atencao: o select do modal de cadastro/edicao NAO oferece {@code inativo} —
 * o operador usa o botao "Arquivar" no card. Por isso o cadastro filtra essa
 * opcao no UI.
 */
export const REAGENT_STATUS_OPTIONS: { value: ReagentStatus; label: string }[] = [
  { value: 'em_estoque', label: 'Em estoque' },
  { value: 'em_uso', label: 'Em uso' },
  { value: 'vencido', label: 'Vencido' },
  { value: 'inativo', label: 'Inativo (arquivado)' },
]

/**
 * Subset oferecido no select de status do {@code ReagentLotModal}. Exclui
 * {@code inativo} — fluxo correto e via "Arquivar" (decisao audit 1.7).
 */
export const REAGENT_STATUS_FORM_OPTIONS = REAGENT_STATUS_OPTIONS.filter(
  (option) => option.value !== 'inativo',
)

/**
 * Tabs do drilldown de etiqueta — espelha o conjunto canonico mais "todos".
 */
export const TAG_STATUS_TABS = [
  'todos',
  'em_estoque',
  'em_uso',
  'vencido',
  'inativo',
] as const

export const REAGENT_STATUS_LABELS: Record<string, string> = {
  em_estoque: 'Em estoque',
  em_uso: 'Em uso',
  vencido: 'Vencido',
  inativo: 'Inativo',
  // Legados (PDFs/audit_log antigos): mantem labels para nao quebrar exibicao.
  fora_de_estoque: 'Fora de estoque (legado)',
  ativo: 'Em estoque (legado)',
  quarentena: 'Quarentena (legado)',
}

/**
 * Tipos de movimento aceitos em escrita pos refator v3. SAIDA aparece apenas
 * em historico (movimentos pre-V14). UI nunca dispara SAIDA — bloqueante audit
 * frontend §4.3.3.
 */
export const MOVEMENT_TYPE_OPTIONS: {
  value: 'ENTRADA' | 'ABERTURA' | 'FECHAMENTO' | 'CONSUMO' | 'AJUSTE'
  label: string
  hint: string
}[] = [
  { value: 'ENTRADA', label: 'Entrada', hint: '+ Em estoque (compra/recebimento)' },
  { value: 'ABERTURA', label: 'Abrir unidade', hint: '-1 Em estoque, +1 Em uso' },
  { value: 'FECHAMENTO', label: 'Voltar ao estoque', hint: '-1 Em uso, +1 Em estoque' },
  // Refator v3.1: rotulo "Final de Uso" — value 'CONSUMO' permanece (contrato HTTP).
  { value: 'CONSUMO', label: 'Final de Uso', hint: 'Encerrar uma unidade aberta — registra a data' },
  { value: 'AJUSTE', label: 'Ajuste manual', hint: 'Define os dois contadores' },
]

export const MOVEMENT_TYPE_LABELS: Record<string, string> = {
  ENTRADA: 'Entrada',
  ABERTURA: 'Abertura',
  FECHAMENTO: 'Fechamento',
  // Refator v3.1: label da UI vira "Final de Uso" (value HTTP CONSUMO inalterado).
  CONSUMO: 'Final de Uso',
  AJUSTE: 'Ajuste',
  SAIDA: 'Saída (legado)',
}
