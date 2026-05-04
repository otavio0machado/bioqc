import type { ReactNode } from 'react'
import { ArrowDownRight, ArrowUpRight } from 'lucide-react'
import { Card } from './Card'
import { cn } from '../../utils/cn'

interface StatCardProps {
  icon: ReactNode
  iconColor: string
  value: string | number
  label: string
  trend?: { value: number; positive: boolean }
}

export function StatCard({ icon, iconColor, value, label, trend }: StatCardProps) {
  return (
    <Card className="space-y-4">
      <div className="flex items-start justify-between">
        <div className={cn('rounded-full p-3 text-white', iconColor)}>{icon}</div>
        {trend ? (
          <span
            className={cn(
              'inline-flex items-center gap-1 rounded-full px-2 py-1 text-xs font-medium',
              trend.positive ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700',
            )}
          >
            {trend.positive ? <ArrowUpRight className="h-3.5 w-3.5" /> : <ArrowDownRight className="h-3.5 w-3.5" />}
            {Math.abs(trend.value)}%
          </span>
        ) : null}
      </div>
      <div>
        <div className="text-4xl font-bold text-neutral-900">{value}</div>
        <div className="mt-1 text-base text-neutral-500">{label}</div>
      </div>
    </Card>
  )
}
