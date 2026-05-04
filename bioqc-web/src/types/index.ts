export type Role = 'ADMIN' | 'FUNCIONARIO' | 'VIGILANCIA_SANITARIA' | 'VISUALIZADOR'
export type QcStatus = 'APROVADO' | 'REPROVADO' | 'ALERTA'
export type ViolationSeverity = 'WARNING' | 'REJECTION'
export type LabArea =
  | 'bioquimica'
  | 'hematologia'
  | 'imunologia'
  | 'parasitologia'
  | 'microbiologia'
  | 'uroanalise'

export interface User {
  id: string
  username: string
  email?: string | null
  name: string
  role: Role
  isActive: boolean
  permissions: string[]
}

export interface AuthResponse {
  accessToken: string
  refreshToken?: string | null
  user: User
}

export interface LoginRequest {
  username: string
  password: string
}

export interface RefreshTokenRequest {
  refreshToken: string
}

export interface PasswordResetResponse {
  message: string
  resetUrl?: string | null
}

export interface ForgotPasswordRequest {
  email: string
}

export interface ResetPasswordRequest {
  token: string
  newPassword: string
}

export interface RegisterRequest {
  username: string
  password: string
  name: string
  role: Role
  email?: string
}

export interface AdminUserRequest {
  username: string
  password: string
  name: string
  role: string
  email?: string
  permissions?: string[]
}

export interface AdminUpdateUserRequest {
  name?: string
  role?: string
  isActive?: boolean
  email?: string
  permissions?: string[]
}

export interface AdminResetPasswordRequest {
  newPassword: string
}

export interface WestgardViolation {
  rule: string
  description: string
  severity: ViolationSeverity
}

export interface QcRecord {
  id: string
  referenceId: string | null
  examName: string
  area: LabArea | string
  date: string
  level: string
  lotNumber: string | null
  value: number
  targetValue: number
  targetSd: number
  cv: number
  cvLimit: number
  zScore: number
  equipment: string | null
  analyst: string | null
  status: QcStatus
  needsCalibration: boolean
  violations: WestgardViolation[]
  createdAt: string
  updatedAt: string
  referenceWarning?: string | null
  postCalibrationValue?: number | null
  postCalibrationCv?: number | null
  postCalibrationStatus?: 'APROVADO' | 'REPROVADO' | null
}

export interface QcRecordRequest {
  examName: string
  area: string
  date: string
  level: string
  lotNumber?: string
  value: number
  targetValue: number
  targetSd: number
  cvLimit: number
  equipment?: string
  analyst?: string
  referenceId?: string
}

export interface QcReferenceValue {
  id: string
  exam: QcExam
  name: string
  level: string
  lotNumber?: string
  manufacturer?: string
  targetValue: number
  targetSd: number
  cvMaxThreshold: number
  validFrom?: string
  validUntil?: string
  isActive: boolean
  notes?: string
}

export interface QcReferenceRequest {
  examId: string
  name: string
  level: string
  lotNumber?: string
  manufacturer?: string
  targetValue: number
  targetSd: number
  cvMaxThreshold: number
  validFrom?: string
  validUntil?: string
  notes?: string
}

export interface QcExam {
  id: string
  name: string
  area: string
  unit?: string
  isActive?: boolean
}

export interface QcExamRequest {
  name: string
  area: string
  unit?: string
}

export interface LeveyJenningsPoint {
  date: string
  value: number
  target: number
  sd: number
  cv: number
  status: QcStatus
  zScore: number
  upper2sd: number
  lower2sd: number
  upper3sd: number
  lower3sd: number
}

export interface PostCalibrationRequest {
  date: string
  postCalibrationValue: number
  analyst?: string
  notes?: string
}

export interface PostCalibrationRecord {
  id: string
  date: string
  examName: string
  originalValue: number
  originalCv: number
  postCalibrationValue: number
  postCalibrationCv: number
  targetValue: number
  analyst?: string
  notes?: string
}

/**
 * Conjunto canonico do status de lote pos refator v3.
 *
 * Mudancas v3:
 * - DROP {@code fora_de_estoque}.
 * - ADD {@code inativo} (terminal manual via endpoint {@code /archive}).
 *
 * Regra de derivacao no backend:
 * - {@code inativo} preserva (estado manual).
 * - {@code expiry < hoje} forca {@code vencido}.
 * - {@code unitsInUse > 0} → {@code em_uso}.
 * - {@code unitsInStock > 0} → {@code em_estoque}.
 */
export type ReagentStatus = 'em_estoque' | 'em_uso' | 'vencido' | 'inativo'

export interface ReagentLot {
  id: string
  /** Substitui o antigo {@code name} no contrato HTTP (backend mantem coluna {@code name}). */
  label: string
  lotNumber: string
  manufacturer: string
  category?: string
  expiryDate: string
  /** Unidades fechadas, prontas para abrir. Substitui {@code currentStock} (drop em V14). */
  unitsInStock: number
  /** Unidades abertas, sendo consumidas. */
  unitsInUse: number
  /** Soma derivada {@code unitsInStock + unitsInUse}. */
  totalUnits: number
  storageTemp?: string
  status: ReagentStatus | string
  createdAt: string
  updatedAt: string
  daysLeft: number
  nearExpiry: boolean
  // Rastreabilidade
  location?: string | null
  supplier?: string | null
  receivedDate?: string | null
  openedDate?: string | null
  // Arquivamento manual (v3)
  archivedAt?: string | null
  archivedBy?: string | null
  /** Lote vindo da migracao V14 com estoque potencialmente nao classificado. */
  needsStockReview?: boolean
  usedInQcRecently?: boolean
  traceabilityComplete?: boolean
  traceabilityIssues?: string[]
  canReceiveEntry?: boolean
  allowedMovementTypes?: StockMovementRequest['type'][]
  movementWarning?: string | null
}

export interface ReagentLotRequest {
  // 10 obrigatorios canonicos
  label: string
  lotNumber: string
  manufacturer: string
  category: string
  unitsInStock: number
  unitsInUse: number
  status: Exclude<ReagentStatus, 'inativo'> | string
  expiryDate: string
  location: string
  storageTemp: string
  // 3 opcionais (Detalhes adicionais)
  supplier?: string
  receivedDate?: string
  openedDate?: string
}

/**
 * Payload do endpoint {@code POST /api/reagents/{id}/archive}.
 * {@code archivedBy} e o {@code username} do responsavel (decisao audit 1.1).
 */
export interface ArchiveReagentLotRequest {
  archivedAt: string
  archivedBy: string
}

export interface UnarchiveReagentLotRequest {
  reason?: string
}

export interface DeleteReagentLotRequest {
  confirmLotNumber: string
}

/**
 * Shape minimo de usuario para combobox de responsavel.
 * Endpoint: {@code GET /api/users/responsibles}.
 */
export interface ResponsibleSummary {
  id: string
  name: string
  username: string
  role: string
}

export type MovementReason =
  | 'CONTAGEM_FISICA'
  | 'QUEBRA'
  | 'CONTAMINACAO'
  | 'CORRECAO'
  | 'REVERSAO_ABERTURA'
  | 'VENCIMENTO'
  | 'OUTRO'

export interface ReportRun {
  id: string
  type: 'QC_PDF' | 'REAGENTS_PDF' | string
  area?: string | null
  periodType?: string | null
  month?: number | null
  year?: number | null
  reportNumber?: string | null
  sha256?: string | null
  sizeBytes?: number | null
  durationMs?: number | null
  status: 'SUCCESS' | 'FAILURE' | string
  errorMessage?: string | null
  username?: string | null
  createdAt: string
}

export interface ImportRun {
  id: string
  source: 'QC_RECORDS' | string
  mode: 'ATOMIC' | 'PARTIAL' | string
  totalRows: number
  successRows: number
  failureRows: number
  durationMs?: number | null
  status: 'SUCCESS' | 'PARTIAL' | 'FAILURE' | string
  errorSummary?: string | null
  username?: string | null
  createdAt: string
}

export interface BatchImportRowResult {
  rowIndex: number
  success: boolean
  message?: string | null
  record?: QcRecord | null
}

export interface BatchImportResult {
  runId: string
  mode: 'ATOMIC' | 'PARTIAL' | string
  total: number
  successCount: number
  failureCount: number
  results: BatchImportRowResult[]
}

/**
 * Tipos de movimento aceitos em escrita pos refator v3.
 *
 * - {@code ENTRADA}: aumenta {@code unitsInStock} (compra/recebimento).
 * - {@code ABERTURA}: -1 unitsInStock, +1 unitsInUse. Quantity sempre 1.
 * - {@code FECHAMENTO}: reverte abertura por engano. -1 unitsInUse, +1 unitsInStock.
 * - {@code CONSUMO}: diminui {@code unitsInUse} (uso real).
 * - {@code AJUSTE}: seta {@code targetUnitsInStock} e {@code targetUnitsInUse}.
 *
 * {@code SAIDA} legado existe apenas em leitura (movimentos pre-V14 conservam o tipo).
 */
export type MovementType = 'ENTRADA' | 'ABERTURA' | 'FECHAMENTO' | 'CONSUMO' | 'AJUSTE'

export interface StockMovement {
  id: string
  /** Aceita {@link MovementType} mais {@code 'SAIDA'} para movimentos legados. */
  type: MovementType | 'SAIDA' | string
  quantity: number
  responsible?: string
  notes?: string
  /** Estoque total antes do movimento (movimentos pre-V14). NULL apos V14. */
  previousStock?: number | null
  /** Unidades fechadas antes do movimento (apenas movimentos pos-V14). NULL antes. */
  previousUnitsInStock?: number | null
  /** Unidades abertas antes do movimento (apenas movimentos pos-V14). NULL antes. */
  previousUnitsInUse?: number | null
  /** True quando {@code previousStock != null AND previousUnitsInStock == null}. */
  isLegacy?: boolean
  reason?: MovementReason | string | null
  /**
   * Data declarada pelo operador para o evento (LocalDate ISO "YYYY-MM-DD").
   * Refator v3.1: ABERTURA usa para gravar {@code lot.openedDate};
   * CONSUMO persiste como data de fim de uso. NULL quando nao informado.
   */
  eventDate?: string | null
  createdAt: string
}

export interface StockMovementRequest {
  type: MovementType
  quantity: number
  responsible: string
  notes?: string
  reason?: MovementReason | null
  /** Obrigatorio para AJUSTE; ignorado para outros tipos. */
  targetUnitsInStock?: number
  targetUnitsInUse?: number
  /**
   * Refator v3.1: data declarada do evento (LocalDate ISO "YYYY-MM-DD").
   * - ABERTURA: usado para preencher {@code lot.openedDate} (default = hoje).
   * - CONSUMO: persiste como data de fim de uso (default = hoje).
   * Backend valida {@code eventDate <= today}.
   */
  eventDate?: string
}

/**
 * Resumo agregado de lotes por etiqueta. Endpoint: {@code GET /api/reagents/labels}.
 *
 * Refator v3: campo {@code foraDeEstoque} renomeado para {@code inativos} (espelha
 * drop de status {@code fora_de_estoque} e add de {@code inativo}).
 */
export interface ReagentLabelSummary {
  label: string
  total: number
  emEstoque: number
  emUso: number
  inativos: number
  vencidos: number
}

export interface MaintenanceRecord {
  id: string
  equipment: string
  type: string
  date: string
  nextDate?: string
  technician?: string
  notes?: string
  createdAt: string
}

export interface MaintenanceRequest {
  equipment: string
  type: string
  date: string
  nextDate?: string
  technician?: string
  notes?: string
}

export interface HematologyQcParameter {
  id: string
  analito: string
  equipamento?: string
  loteControle?: string
  nivelControle?: string
  modo: 'INTERVALO' | 'PERCENTUAL' | string
  alvoValor: number
  minValor: number
  maxValor: number
  toleranciaPercentual: number
  isActive: boolean
  createdAt?: string
  updatedAt?: string
}

export interface HematologyParameterRequest {
  analito: string
  equipamento?: string
  loteControle?: string
  nivelControle?: string
  modo: 'INTERVALO' | 'PERCENTUAL'
  alvoValor?: number
  minValor?: number
  maxValor?: number
  toleranciaPercentual?: number
}

export interface HematologyQcMeasurement {
  id: string
  parameterId: string
  parameterEquipamento?: string
  parameterLoteControle?: string
  parameterNivelControle?: string
  dataMedicao: string
  analito: string
  valorMedido: number
  modoUsado?: string
  minAplicado: number
  maxAplicado: number
  status: 'APROVADO' | 'REPROVADO'
  observacao?: string
  createdAt?: string
}

export interface HematologyMeasurementRequest {
  parameterId: string
  dataMedicao: string
  analito: string
  valorMedido: number
  observacao?: string
}

export interface HematologyBioRecord {
  id: string
  dataBio: string
  dataPad?: string
  registroBio?: string
  registroPad?: string
  modoCi?: string
  bioHemacias: number
  bioHematocrito: number
  bioHemoglobina: number
  bioLeucocitos: number
  bioPlaquetas: number
  bioRdw: number
  bioVpm: number
  padHemacias: number
  padHematocrito: number
  padHemoglobina: number
  padLeucocitos: number
  padPlaquetas: number
  padRdw: number
  padVpm: number
  ciMinHemacias: number
  ciMaxHemacias: number
  ciMinHematocrito: number
  ciMaxHematocrito: number
  ciMinHemoglobina: number
  ciMaxHemoglobina: number
  ciMinLeucocitos: number
  ciMaxLeucocitos: number
  ciMinPlaquetas: number
  ciMaxPlaquetas: number
  ciMinRdw: number
  ciMaxRdw: number
  ciMinVpm: number
  ciMaxVpm: number
  ciPctHemacias: number
  ciPctHematocrito: number
  ciPctHemoglobina: number
  ciPctLeucocitos: number
  ciPctPlaquetas: number
  ciPctRdw: number
  ciPctVpm: number
}

export type HematologyBioRequest = Omit<HematologyBioRecord, 'id'>

export interface DashboardKpi {
  totalToday: number
  totalMonth: number
  approvalRate: number
  hasAlerts: boolean
  alertsCount: number
}

export interface AlertSection<T> {
  count: number
  items: T[]
}

export interface DashboardAlerts {
  expiringReagents: AlertSection<ReagentLot>
  pendingMaintenances: AlertSection<MaintenanceRecord>
  westgardViolations: AlertSection<QcRecord>
}

export interface AiAnalysisRequest {
  prompt: string
  context?: string
  area?: string
  examName?: string
  days?: number
}

export interface VoiceToFormRequest {
  audioBase64: string
  formType: 'registro' | 'referencia' | 'reagente' | 'manutencao'
  mimeType?: string
}

export type VoiceFormData = Record<string, string | number | null>

export interface AreaQcParameter {
  id: string
  area: LabArea | string
  analito: string
  equipamento?: string | null
  loteControle?: string | null
  nivelControle?: string | null
  modo: 'INTERVALO' | 'PERCENTUAL' | string
  alvoValor: number
  minValor?: number | null
  maxValor?: number | null
  toleranciaPercentual?: number | null
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface AreaQcParameterRequest {
  analito: string
  equipamento?: string
  loteControle?: string
  nivelControle?: string
  modo: 'INTERVALO' | 'PERCENTUAL'
  alvoValor: number
  minValor?: number
  maxValor?: number
  toleranciaPercentual?: number
}

export interface AreaQcMeasurement {
  id: string
  parameterId?: string | null
  parameterEquipamento?: string | null
  parameterLoteControle?: string | null
  parameterNivelControle?: string | null
  area: LabArea | string
  dataMedicao: string
  analito: string
  valorMedido: number
  modoUsado: 'INTERVALO' | 'PERCENTUAL' | string
  minAplicado: number
  maxAplicado: number
  status: 'APROVADO' | 'REPROVADO'
  observacao?: string | null
  createdAt: string
}

export interface AreaQcMeasurementRequest {
  dataMedicao: string
  analito: string
  valorMedido: number
  parameterId?: string
  equipamento?: string
  loteControle?: string
  nivelControle?: string
  observacao?: string
}

export interface ImportedQcPreviewRow extends QcRecordRequest {
  previewId: string
}
