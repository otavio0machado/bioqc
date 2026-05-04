import { cn } from '../../utils/cn'

interface SkeletonProps {
  type?: 'block' | 'circle' | 'line'
  width?: string
  height?: string
  lines?: number
  className?: string
}

export function Skeleton({
  type = 'block',
  width = '100%',
  height = '1rem',
  lines = 3,
  className,
}: SkeletonProps) {
  if (type === 'line') {
    return (
      <div className={cn('space-y-2', className)}>
        {Array.from({ length: lines }).map((_, index) => (
          <div
            key={index}
            className="animate-pulse rounded-md bg-neutral-200"
            style={{ width: index === lines - 1 ? '75%' : width, height }}
          />
        ))}
      </div>
    )
  }

  return (
    <div
      className={cn(
        'animate-pulse bg-neutral-200',
        type === 'circle' ? 'rounded-full' : 'rounded-xl',
        className,
      )}
      style={{ width, height }}
    />
  )
}
