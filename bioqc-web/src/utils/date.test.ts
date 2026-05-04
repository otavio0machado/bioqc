import { describe, expect, it } from 'vitest'
import {
  compareLocalDate,
  diffInDays,
  formatLongBR,
  formatShortBR,
  parseLocalDate,
  todayLocal,
} from './date'

describe('utils/date', () => {
  describe('parseLocalDate', () => {
    it('retorna null para strings vazias / nulas', () => {
      expect(parseLocalDate(null)).toBeNull()
      expect(parseLocalDate(undefined)).toBeNull()
      expect(parseLocalDate('')).toBeNull()
      expect(parseLocalDate('   ')).toBeNull()
    })

    it('interpreta LocalDate puro como meia-noite LOCAL (nao UTC)', () => {
      // Bug original: new Date("2026-04-16") era meia-noite UTC, voltava 15/04 em
      // fuso negativo. O util forca "T00:00:00" que e interpretado em local time.
      const d = parseLocalDate('2026-04-16')!
      expect(d).not.toBeNull()
      expect(d.getFullYear()).toBe(2026)
      expect(d.getMonth()).toBe(3) // Abril = 3 (base zero)
      expect(d.getDate()).toBe(16)
    })

    it('retorna null em string invalida', () => {
      expect(parseLocalDate('xyz')).toBeNull()
    })

    it('aceita ISO com timezone (Instant) delegando ao Date nativo', () => {
      const d = parseLocalDate('2026-04-16T12:30:00Z')!
      expect(d).not.toBeNull()
      expect(d.getTime()).toBe(new Date('2026-04-16T12:30:00Z').getTime())
    })
  })

  describe('formatShortBR / formatLongBR', () => {
    it('formata dd/MM e dd/MM/yyyy', () => {
      expect(formatShortBR('2026-04-16')).toBe('16/04')
      expect(formatLongBR('2026-04-16')).toBe('16/04/2026')
    })

    it('retorna string vazia em input vazio', () => {
      expect(formatShortBR('')).toBe('')
      expect(formatLongBR(null)).toBe('')
    })
  })

  describe('todayLocal', () => {
    it('retorna LocalDate do dia atual no fuso local em formato yyyy-MM-dd', () => {
      const now = new Date()
      const expected = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
      expect(todayLocal()).toBe(expected)
    })
  })

  describe('compareLocalDate', () => {
    it('sort crescente', () => {
      const arr = ['2026-04-20', '2026-04-10', '2026-04-15']
      arr.sort(compareLocalDate)
      expect(arr).toEqual(['2026-04-10', '2026-04-15', '2026-04-20'])
    })

    it('trata null como inicio da epoca', () => {
      expect(compareLocalDate(null, '2026-04-10')).toBeLessThan(0)
    })
  })

  describe('diffInDays', () => {
    it('retorna numero positivo quando b > a', () => {
      expect(diffInDays('2026-04-10', '2026-04-16')).toBe(6)
    })

    it('retorna numero negativo quando b < a', () => {
      expect(diffInDays('2026-04-16', '2026-04-10')).toBe(-6)
    })

    it('retorna null se alguma data e invalida', () => {
      expect(diffInDays('xyz', '2026-04-10')).toBeNull()
      expect(diffInDays('2026-04-10', null)).toBeNull()
    })
  })
})
