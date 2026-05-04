/**
 * Tipos V2 de Relatorios - alinhados ao contrato real exposto por
 * {@code /api/reports/v2/**}. Backend usa {@code ReportDefinitionResponse}
 * com {@code filters: FilterFieldDto[]} (nao aninhado em filterSpec); no
 * frontend exibimos uma visao aninhada {@code filterSpec.fields} por
 * conveniencia - o mapeamento e feito no service.
 */

/**
 * Codigos estaveis expostos pelo backend (Reports V2 expandido).
 *
 * <p>Mantemos a union como aberta ({@code (string & {})}) para nao quebrar
 * a UI se o backend registrar um code novo antes do deploy do frontend -
 * mas os 7 codes listados aqui sao a enumeracao completa do catalogo F2.
 */
export type ReportCode =
  | 'CQ_OPERATIONAL_V2'
  | 'WESTGARD_DEEPDIVE'
  | 'REAGENTES_RASTREABILIDADE'
  | 'MANUTENCAO_KPI'
  | 'CALIBRACAO_PREPOST'
  | 'MULTI_AREA_CONSOLIDADO'
  | 'REGULATORIO_PACOTE'
  | (string & {})

/**
 * Categorias usadas no catalogo (alinhadas ao enum do backend).
 * HEMATOLOGIA mantida por compatibilidade com V1 que ainda coexiste.
 */
export type ReportCategory =
  | 'CONTROLE_QUALIDADE'
  | 'WESTGARD'
  | 'REAGENTES'
  | 'MANUTENCAO'
  | 'CALIBRACAO'
  | 'CONSOLIDADO'
  | 'REGULATORIO'
  | 'HEMATOLOGIA'
  | (string & {})

export type ReportFormat = 'PDF' | 'HTML' | 'XLSX'

export type ReportFilterFieldType =
  | 'STRING_ENUM'
  | 'STRING_ENUM_MULTI'
  | 'STRING'
  | 'INTEGER'
  | 'DATE'
  | 'DATE_RANGE'
  | 'UUID'
  | 'UUID_LIST'
  | 'BOOLEAN'

export interface ReportFilterField {
  key: string
  type: ReportFilterFieldType
  required: boolean
  allowedValues: string[] | null
  label: string
  helpText: string | null
}

export interface ReportFilterSpec {
  fields: ReportFilterField[]
}

export interface ReportDefinition {
  code: ReportCode
  name: string
  description: string
  /** Subtitulo curto exibido no card do catalogo. Pode ser vazio. */
  subtitle: string
  /**
   * Nome logico do icone devolvido pelo backend. Atualmente o backend emite
   * nomes heroicons (ex.: {@code clipboard-document-check}); o frontend
   * mapeia para lucide-react em {@code ReportCatalogGrid}.
   */
  icon: string
  category: ReportCategory
  supportedFormats: ReportFormat[]
  filterSpec: ReportFilterSpec
  roleAccess: string[]
  signatureRequired: boolean
  previewSupported: boolean
  /**
   * Indica se o relatorio aceita o toggle {@code includeAiCommentary}.
   * Quando false, a UI oculta o campo mesmo que ele apareca na spec.
   */
  aiCommentaryCapable: boolean
  retentionDays: number
  legalBasis: string
}

/**
 * Resposta canonica de execucao V2. Alinha ao record Java {@code ReportExecutionResponse}.
 *
 * <p>Campos de assinatura:
 * <ul>
 *   <li>{@code sha256}: hash do PDF original</li>
 *   <li>{@code signatureHash}: hash apos assinatura (null ate /sign)</li>
 *   <li>{@code signedSha256}: alias explicito de {@code signatureHash} para
 *     contratos novos que querem discriminar sem ambiguidade.</li>
 * </ul>
 *
 * <p>{@code labels} e {@code warnings} sao sempre nao-nulos.
 */
export interface ReportExecutionResponse {
  id: string
  reportCode: ReportCode
  format: ReportFormat
  /** SUCCESS | WITH_WARNINGS | FAILURE | SIGNED (string aberta para evolucao). */
  status: 'SUCCESS' | 'WITH_WARNINGS' | 'FAILURE' | 'SIGNED' | (string & {})
  reportNumber: string | null
  sha256: string | null
  signatureHash: string | null
  signedSha256: string | null
  sizeBytes: number | null
  pageCount: number | null
  username: string | null
  createdAt: string
  signedAt: string | null
  expiresAt: string | null
  downloadUrl: string | null
  verifyUrl: string | null
  periodLabel: string | null
  /** Rotulos associados (ex.: "oficial_mensal"). Sempre array (pode ser vazio). */
  labels: string[]
  /** Avisos persistidos da geracao, como pacote parcial ou ausencia de dados. */
  warnings: string[]
}

export interface PreviewResponse {
  html: string
  warnings: string[]
  periodLabel: string
}

export interface VerifyReportResponse {
  reportNumber: string | null
  reportCode: ReportCode | null
  periodLabel: string | null
  generatedAt: string | null
  generatedByName: string | null
  sha256: string | null
  signatureHash: string | null
  signedAt: string | null
  signedByName: string | null
  signed: boolean
  valid: boolean
}

export interface GenerateReportRequest {
  code: ReportCode
  filters: Record<string, unknown>
  format?: ReportFormat
  /**
   * Hint para assinar imediatamente apos gerar. O backend atual ignora se
   * nao implementado; o hook chama /sign em sequencia quando true.
   */
  signImmediately?: boolean
}

export interface PreviewRequest {
  code: ReportCode
  filters: Record<string, unknown>
}

export interface SignReportRequest {
  signerName?: string
  signerRegistration?: string
}

/**
 * Payload para aplicar/remover rotulos em uma execucao V2. Tanto {@code add}
 * quanto {@code remove} sao opcionais no backend, mas sempre enviamos arrays
 * (possivelmente vazios) para simplificar o calculo do diff no cliente.
 */
export interface SetReportLabelsRequest {
  add: string[]
  remove: string[]
}

export interface ExecutionsFilter {
  code?: ReportCode
  status?: string
  from?: string
  to?: string
  /** Nao suportado pelo backend F1 - reservado para slice futuro. */
  user?: string
  page?: number
  size?: number
}

/** Formato da pagina devolvida pelo Spring Data. */
export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

/**
 * Forma crua que o backend devolve para a definicao. O service achata
 * para {@link ReportDefinition} (com filterSpec aninhado).
 */
export interface ReportDefinitionRawResponse {
  code: ReportCode
  name: string
  description: string
  subtitle?: string
  icon?: string
  category: ReportCategory
  supportedFormats: ReportFormat[] | string[]
  filters: ReportFilterField[]
  roleAccess: string[]
  signatureRequired: boolean
  previewSupported: boolean
  aiCommentaryCapable?: boolean
  retentionDays: number
  legalBasis: string
}
