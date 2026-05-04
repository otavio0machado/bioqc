import type { User } from '../types'

export function canDownload(user: User | null): boolean {
  if (!user) return false
  if (user.role === 'ADMIN' || user.role === 'VIGILANCIA_SANITARIA') return true
  if (user.role === 'FUNCIONARIO') return user.permissions.includes('DOWNLOAD')
  return false
}

export function canWriteQc(user: User | null): boolean {
  if (!user) return false
  if (user.role === 'ADMIN') return true
  if (user.role === 'FUNCIONARIO') return user.permissions.includes('QC_WRITE')
  return false
}

export function canWriteReagent(user: User | null): boolean {
  if (!user) return false
  if (user.role === 'ADMIN') return true
  if (user.role === 'FUNCIONARIO') return user.permissions.includes('REAGENT_WRITE')
  return false
}

export function canWriteMaintenance(user: User | null): boolean {
  if (!user) return false
  if (user.role === 'ADMIN') return true
  if (user.role === 'FUNCIONARIO') return user.permissions.includes('MAINTENANCE_WRITE')
  return false
}

export function canImport(user: User | null): boolean {
  if (!user) return false
  if (user.role === 'ADMIN') return true
  if (user.role === 'FUNCIONARIO') return user.permissions.includes('IMPORT')
  return false
}

export function isReadOnly(user: User | null): boolean {
  if (!user) return true
  return user.role === 'VISUALIZADOR' || user.role === 'VIGILANCIA_SANITARIA'
}

export const ROLE_LABELS: Record<string, string> = {
  ADMIN: 'Administrador',
  FUNCIONARIO: 'Funcionário',
  VIGILANCIA_SANITARIA: 'Vigilância Sanitária',
  VISUALIZADOR: 'Visualizador',
}

export const PERMISSION_LABELS: Record<string, string> = {
  QC_WRITE: 'Registrar CQ',
  REAGENT_WRITE: 'Gerenciar Reagentes',
  MAINTENANCE_WRITE: 'Registrar Manutenção',
  DOWNLOAD: 'Baixar Relatórios',
  IMPORT: 'Importar Dados',
}

export const ALL_PERMISSIONS = ['QC_WRITE', 'REAGENT_WRITE', 'MAINTENANCE_WRITE', 'DOWNLOAD', 'IMPORT'] as const
