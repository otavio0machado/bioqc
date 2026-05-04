import type { HTMLAttributes, ReactNode } from 'react'
import { cn } from '../../utils/cn'

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode
  glass?: boolean
  onClick?: () => void
}

export function Card({ children, className, glass = false, onClick, ...props }: CardProps) {
  return (
    <div
      className={cn(
        'rounded-2xl p-6 shadow-card transition-shadow',
        glass ? 'border border-white/20 bg-white/70 backdrop-blur-md' : 'bg-white',
        onClick && 'cursor-pointer hover:shadow-elevated',
        className,
      )}
      onClick={onClick}
      {...props}
    >
      {children}
    </div>
  )
}
