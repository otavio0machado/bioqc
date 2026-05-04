import type { ReactNode, TextareaHTMLAttributes } from 'react'
import { forwardRef } from 'react'
import { cn } from '../../utils/cn'

interface TextAreaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string
  error?: string
  icon?: ReactNode
}

export const TextArea = forwardRef<HTMLTextAreaElement, TextAreaProps>(function TextArea(
  { className, label, error, id, ...props },
  ref,
) {
  const fieldId = id ?? props.name
  return (
    <label className="block space-y-1" htmlFor={fieldId}>
      {label ? <span className="text-sm font-medium text-neutral-700">{label}</span> : null}
      <textarea
        ref={ref}
        id={fieldId}
        className={cn(
          'min-h-28 w-full rounded-xl border bg-white px-4 py-3 text-sm transition outline-none',
          error
            ? 'border-red-500 focus:border-red-500 focus:ring-2 focus:ring-red-500/20'
            : 'border-neutral-200 focus:border-green-800 focus:ring-2 focus:ring-green-800/20',
          className,
        )}
        {...props}
      />
      {error ? <p className="text-sm text-red-500">{error}</p> : null}
    </label>
  )
})
