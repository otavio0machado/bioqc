/**
 * Utilitarios de data.
 *
 * O backend serializa campos LocalDate como string ISO curta "YYYY-MM-DD"
 * (sem horario/timezone). Se passarmos direto para `new Date("YYYY-MM-DD")`,
 * o navegador interpreta como UTC meia-noite e, em timezones negativas
 * (ex: America/Sao_Paulo = UTC-3), o objeto Date aponta para o dia anterior.
 *
 * Estas funcoes centralizam a conversao para o fuso local.
 */

/**
 * Converte uma string ISO LocalDate ("YYYY-MM-DD") em Date local.
 * Aceita tambem strings ja com timezone (Instant) — devolve Date direto.
 * Retorna `null` se a string for vazia/invalida.
 */
export function parseLocalDate(value: string | null | undefined): Date | null {
  if (!value) return null
  const trimmed = String(value).trim()
  if (!trimmed) return null

  // Ja tem indicador de hora/timezone -> delegar ao Date nativo
  if (trimmed.includes('T') || /[zZ+]|(\d{2}:\d{2})/.test(trimmed)) {
    const d = new Date(trimmed)
    return isNaN(d.getTime()) ? null : d
  }

  // LocalDate puro "YYYY-MM-DD" -> forcar meia-noite local
  const d = new Date(`${trimmed}T00:00:00`)
  return isNaN(d.getTime()) ? null : d
}

/** Formata LocalDate como "dd/MM" (eixo de graficos, listas compactas). */
export function formatShortBR(value: string | null | undefined): string {
  const d = parseLocalDate(value)
  if (!d) return ''
  return d.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })
}

/** Formata LocalDate como "dd/MM/yyyy". */
export function formatLongBR(value: string | null | undefined): string {
  const d = parseLocalDate(value)
  if (!d) return ''
  return d.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric' })
}

/** Retorna LocalDate de hoje no fuso local ("YYYY-MM-DD"). */
export function todayLocal(): string {
  const d = new Date()
  const yyyy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd}`
}

/**
 * Compara duas LocalDate strings. Retorna negativo, zero ou positivo,
 * seguindo o contrato de Array.prototype.sort.
 */
export function compareLocalDate(a?: string | null, b?: string | null): number {
  const da = parseLocalDate(a)?.getTime() ?? 0
  const db = parseLocalDate(b)?.getTime() ?? 0
  return da - db
}

/**
 * Diferenca em dias entre duas LocalDate (b - a). Positivo = b esta no futuro.
 * Retorna null se alguma das datas for invalida.
 */
export function diffInDays(a?: string | null, b?: string | null): number | null {
  const da = parseLocalDate(a)
  const db = parseLocalDate(b)
  if (!da || !db) return null
  const MS_DAY = 24 * 60 * 60 * 1000
  return Math.round((db.getTime() - da.getTime()) / MS_DAY)
}
