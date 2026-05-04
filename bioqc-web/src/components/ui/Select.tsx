import type { ReactNode, SelectHTMLAttributes } from 'react'
import { forwardRef } from 'react'
import { cn } from '../../utils/cn'

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: string
  error?: string
  icon?: ReactNode
}

export const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select(
  { className, label, error, icon, id, children, ...props },
  ref,
) {
  const fieldId = id ?? props.name
  return (
    <label className="block space-y-1" htmlFor={fieldId}>
      {label ? <span className="text-base font-medium text-neutral-700">{label}</span> : null}
      <div
        className={cn(
          'flex items-center gap-3 rounded-xl border bg-white px-4 py-3 transition focus-within:ring-2',
          error
            ? 'border-red-500 focus-within:border-red-500 focus-within:ring-red-500/20'
            : 'border-neutral-200 focus-within:border-green-800 focus-within:ring-green-800/20',
        )}
      >
        {icon ? <span className="text-neutral-400">{icon}</span> : null}
        <select
          ref={ref}
          id={fieldId}
          className={cn('w-full border-none bg-transparent text-base outline-none', className)}
          {...props}
        >
          {children}
        </select>
      </div>
      {error ? <p className="text-sm text-red-500">{error}</p> : null}
    </label>
  )
})
