import { cn } from '../../utils/cn'

interface StatusBadgeProps {
  status:
    | 'APROVADO'
    | 'REPROVADO'
    | 'ALERTA'
    // Reagentes — conjunto canonico pos refator v2
    | 'em_estoque'
    | 'em_uso'
    | 'fora_de_estoque'
    | 'vencido'
    // Reagentes — legados aceitos para compatibilidade visual
    | 'ativo'
    | 'inativo'
    | 'quarentena'
    | string
}

const badgeMap: Record<string, string> = {
  APROVADO: 'bg-green-100 text-green-800',
  REPROVADO: 'bg-red-100 text-red-800',
  ALERTA: 'bg-amber-100 text-amber-800',
  // Reagentes (novos)
  em_estoque: 'bg-green-100 text-green-800',
  em_uso: 'bg-blue-100 text-blue-800',
  fora_de_estoque: 'bg-neutral-100 text-neutral-600',
  vencido: 'bg-red-100 text-red-800',
  // Reagentes (legados — sao mapeados para o novo no backend, mas o badge resiste a stale data)
  ativo: 'bg-green-100 text-green-800',
  inativo: 'bg-neutral-100 text-neutral-600',
  quarentena: 'bg-amber-100 text-amber-800',
}

const labelMap: Record<string, string> = {
  em_estoque: 'Em estoque',
  em_uso: 'Em uso',
  fora_de_estoque: 'Fora de estoque',
  vencido: 'Vencido',
  ativo: 'Ativo',
  inativo: 'Inativo',
  quarentena: 'Quarentena',
}

export function StatusBadge({ status }: StatusBadgeProps) {
  const text = labelMap[status] ?? status.replace(/_/g, ' ')
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full px-3 py-1.5 text-sm font-semibold',
        badgeMap[status] ?? 'bg-neutral-100 text-neutral-600',
      )}
    >
      {text}
    </span>
  )
}
