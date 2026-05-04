import type { ReactNode } from 'react'

interface FormFieldProps {
  label?: string
  error?: string
  children: ReactNode
}

export function FormField({ label, error, children }: FormFieldProps) {
  return (
    <div className="space-y-1">
      {label ? <div className="text-sm font-medium text-neutral-700">{label}</div> : null}
      {children}
      {error ? <p className="text-sm text-red-500">{error}</p> : null}
    </div>
  )
}
