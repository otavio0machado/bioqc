/**
 * Rotulos conhecidos para execucoes de Reports V2.
 *
 * <p>Mantidos hardcoded aqui porque o backend aceita qualquer string
 * (lowercase snake_case) como label, mas a UI oferece apenas estes 4
 * como toggles pre-definidos. Um label "livre" vindo do backend continua
 * sendo renderizado como chip neutro em {@link ExecutionsTable}.
 */
export interface ReportLabelOption {
  /** Codigo lowercase snake_case persistido no backend. */
  code: string
  /** Titulo exibido na UI (chip + modal). */
  label: string
  /** Classes Tailwind para o chip (bg + text). */
  color: string
}

export const REPORT_LABELS: ReportLabelOption[] = [
  {
    code: 'oficial_mensal',
    label: 'Oficial Mensal',
    color: 'bg-green-100 text-green-800',
  },
  {
    code: 'entregue_vigilancia',
    label: 'Entregue a Vigilancia',
    color: 'bg-blue-100 text-blue-800',
  },
  {
    code: 'rascunho',
    label: 'Rascunho',
    color: 'bg-neutral-100 text-neutral-600',
  },
  {
    code: 'revisao_interna',
    label: 'Revisao Interna',
    color: 'bg-amber-100 text-amber-800',
  },
]

/** Resolve a configuracao do chip para um label vindo do backend. */
export function findLabelOption(code: string): ReportLabelOption | undefined {
  return REPORT_LABELS.find((option) => option.code === code)
}
