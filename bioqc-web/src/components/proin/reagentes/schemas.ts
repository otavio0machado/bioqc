import { z } from 'zod'
import type { ReagentLot, ReagentLotRequest, StockMovementRequest } from '../../../types'
import { CATEGORIES, REAGENT_STATUS_FORM_OPTIONS, TEMPS } from './constants'
import { todayLocal } from '../../../utils/date'

interface ValidationResult {
  message: string
}

const STATUS_FORM_VALUES = REAGENT_STATUS_FORM_OPTIONS.map((option) => option.value)

/**
 * Validacao zod do form de cadastro/edicao de lote pos-refator v3.
 *
 * Regras canonicas (espelha o contrato §4.1):
 * - 10 obrigatorios canonicos: label, lotNumber, manufacturer, category,
 *   unitsInStock, unitsInUse, status, expiryDate, location, storageTemp.
 * - {@code status='inativo'} e REJEITADO no cadastro/edicao (use {@code /archive}).
 * - {@code receivedDate <= openedDate <= expiryDate} quando ambos presentes.
 * - {@code label} e {@code lotNumber} sao trimados; falha se vazios apos trim.
 */
const lotSchema = z
  .object({
    label: z.string().trim().min(1, 'Informe a etiqueta do lote.'),
    lotNumber: z.string().trim().min(1, 'Informe o número do lote.'),
    manufacturer: z.string().trim().min(1, 'Informe o fabricante do lote.'),
    category: z.string().refine((value) => CATEGORIES.includes(value), {
      message: 'Selecione uma categoria válida.',
    }),
    unitsInStock: z
      .number({ error: 'Informe quantas unidades estão em estoque.' })
      .int('Unidades devem ser inteiros.')
      .min(0, 'Em estoque não pode ser negativo.'),
    unitsInUse: z
      .number({ error: 'Informe quantas unidades estão em uso.' })
      .int('Unidades devem ser inteiros.')
      .min(0, 'Em uso não pode ser negativo.'),
    status: z.string().refine((value) => STATUS_FORM_VALUES.includes(value as never), {
      message: 'Status "Inativo" exige usar o botão Arquivar do lote.',
    }),
    expiryDate: z.string().trim().min(1, 'Informe a data de validade.'),
    location: z.string().trim().min(1, 'Informe a localização física do lote.'),
    storageTemp: z.string().refine((value) => TEMPS.includes(value), {
      message: 'Selecione uma temperatura válida.',
    }),
    receivedDate: z.string().trim().optional().or(z.literal('')),
    openedDate: z.string().trim().optional().or(z.literal('')),
    supplier: z.string().trim().optional().or(z.literal('')),
  })
  .superRefine((value, ctx) => {
    const expiry = value.expiryDate?.trim()
    const received = value.receivedDate?.trim()
    const opened = value.openedDate?.trim()

    if (received && expiry && expiry < received) {
      ctx.addIssue({
        code: 'custom',
        path: ['expiryDate'],
        message: 'A data de validade não pode ser anterior à data de recebimento.',
      })
    }

    if (opened && expiry && expiry < opened) {
      ctx.addIssue({
        code: 'custom',
        path: ['expiryDate'],
        message: 'A data de validade não pode ser anterior à data de abertura.',
      })
    }

    if (received && opened && opened < received) {
      ctx.addIssue({
        code: 'custom',
        path: ['openedDate'],
        message: 'A data de abertura não pode ser anterior ao recebimento.',
      })
    }

    const total = (value.unitsInStock ?? 0) + (value.unitsInUse ?? 0)
    if (total === 0 && value.status !== 'vencido') {
      ctx.addIssue({
        code: 'custom',
        path: ['unitsInStock'],
        message: 'Informe pelo menos uma unidade (em estoque ou em uso).',
      })
    }
  })

/**
 * Tipos aceitos em escrita pos refator v3. {@code SAIDA} e descontinuado —
 * UI nunca dispara (bloqueante audit frontend §4.3.3).
 */
const movementTypeSchema = z.enum(['ENTRADA', 'ABERTURA', 'FECHAMENTO', 'CONSUMO', 'AJUSTE'])

const movementSchema = z
  .object({
    type: movementTypeSchema,
    quantity: z.number().min(0, 'Quantidade inválida.'),
    responsible: z.string().trim().min(1, 'Informe o responsável pela movimentação.'),
    reason: z.string().nullable().optional(),
    targetUnitsInStock: z.number().int().min(0).optional(),
    targetUnitsInUse: z.number().int().min(0).optional(),
    /**
     * Refator v3.1: data declarada do evento (LocalDate "YYYY-MM-DD"). Opcional.
     * Quando preenchida, deve ser <= hoje. Backend tambem valida.
     */
    eventDate: z.string().optional(),
  })
  .superRefine((value, ctx) => {
    if (value.eventDate && value.eventDate.trim()) {
      const today = todayLocal()
      if (value.eventDate > today) {
        ctx.addIssue({
          code: 'custom',
          path: ['eventDate'],
          message: 'A data não pode ser futura.',
        })
      }
    }
  })

export function validateLotForm(form: ReagentLotRequest): ValidationResult | null {
  const result = lotSchema.safeParse(form)
  if (!result.success) {
    return { message: result.error.issues[0]?.message ?? 'Erro ao validar lote.' }
  }
  return null
}

/**
 * Valida payload de movimento contra o estado atual do lote.
 *
 * Regras refator v3:
 * - ENTRADA: bloqueada se {@code !canReceiveEntry}; {@code quantity > 0}.
 * - ABERTURA: {@code quantity == 1}; {@code unitsInStock >= 1}.
 * - FECHAMENTO: {@code quantity == 1}; {@code unitsInUse >= 1}.
 * - CONSUMO: {@code quantity > 0}; {@code unitsInUse >= quantity};
 *            em vencido exige reason.
 * - AJUSTE: {@code targetUnitsInStock} e {@code targetUnitsInUse} obrigatorios;
 *           reason obrigatorio.
 */
export function validateMovementForm(
  form: StockMovementRequest,
  lot: Pick<ReagentLot, 'unitsInStock' | 'unitsInUse' | 'status' | 'canReceiveEntry'>,
): ValidationResult | null {
  const result = movementSchema.safeParse(form)
  if (!result.success) {
    return { message: result.error.issues[0]?.message ?? 'Erro ao validar movimentação.' }
  }

  const unitsInStock = lot.unitsInStock ?? 0
  const unitsInUse = lot.unitsInUse ?? 0
  const isVencido = lot.status === 'vencido'
  const isInativo = lot.status === 'inativo'

  switch (form.type) {
    case 'ENTRADA': {
      const canEntry =
        typeof lot.canReceiveEntry === 'boolean'
          ? lot.canReceiveEntry
          : !isVencido && !isInativo
      if (!canEntry) {
        return {
          message: isInativo
            ? 'Lote inativo não aceita ENTRADA. Reative o lote antes.'
            : 'Lote vencido não aceita ENTRADA. Crie um novo lote.',
        }
      }
      if (form.quantity <= 0) return { message: 'Informe quantidade > 0 para a ENTRADA.' }
      break
    }
    case 'ABERTURA': {
      if (isVencido || isInativo) {
        return { message: 'Lote vencido ou inativo não permite abrir unidade.' }
      }
      if (form.quantity !== 1) {
        return { message: 'Abertura opera unitariamente (quantidade = 1).' }
      }
      if (unitsInStock < 1) return { message: 'Sem unidades em estoque para abrir.' }
      break
    }
    case 'FECHAMENTO': {
      if (isVencido || isInativo) {
        return { message: 'Lote vencido ou inativo não permite voltar unidade ao estoque.' }
      }
      if (form.quantity !== 1) {
        return { message: 'Fechamento opera unitariamente (quantidade = 1).' }
      }
      if (unitsInUse < 1) return { message: 'Sem unidades em uso para voltar ao estoque.' }
      break
    }
    case 'CONSUMO': {
      if (isInativo) {
        return { message: 'Lote inativo não aceita CONSUMO. Reative ou faça AJUSTE.' }
      }
      if (form.quantity <= 0) return { message: 'Informe quantidade > 0 para o CONSUMO.' }
      if (form.quantity > unitsInUse) {
        return { message: `Sem unidades em uso suficientes (${unitsInUse}).` }
      }
      if (isVencido && !form.reason) {
        return { message: 'CONSUMO em lote vencido exige motivo (descarte registrado).' }
      }
      break
    }
    case 'AJUSTE': {
      if (
        typeof form.targetUnitsInStock !== 'number' ||
        typeof form.targetUnitsInUse !== 'number'
      ) {
        return { message: 'AJUSTE exige preencher Em estoque e Em uso desejados.' }
      }
      if (form.targetUnitsInStock < 0 || form.targetUnitsInUse < 0) {
        return { message: 'Valores de AJUSTE não podem ser negativos.' }
      }
      if (!form.reason) {
        return { message: 'AJUSTE exige um motivo.' }
      }
      break
    }
  }

  return null
}
