import { api } from './api'
import type { ResponsibleSummary } from '../types'

/**
 * Endpoints de leitura sobre usuarios para uso operacional.
 *
 * {@link getResponsibles} alimenta o combobox de "Responsavel" em arquivamento
 * de lote (refator v3). Retorna apenas usuarios ativos com role
 * {@code ADMIN | FUNCIONARIO}.
 */
export const userService = {
  async getResponsibles() {
    const response = await api.get<ResponsibleSummary[]>('/users/responsibles')
    return response.data
  },
}
