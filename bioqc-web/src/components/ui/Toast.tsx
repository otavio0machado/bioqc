import {
  useCallback,
  useMemo,
  useState,
  type PropsWithChildren,
} from 'react'
import { AlertTriangle, CheckCircle2, Info, XCircle } from 'lucide-react'
import { cn } from '../../utils/cn'
import { ToastContext, type ToastContextValue, type ToastItem } from './toast-context'

const toastStyles = {
  success: {
    wrapper: 'border-green-200 bg-white text-green-800',
    icon: <CheckCircle2 className="h-5 w-5" />,
  },
  error: {
    wrapper: 'border-red-200 bg-white text-red-800',
    icon: <XCircle className="h-5 w-5" />,
  },
  warning: {
    wrapper: 'border-amber-200 bg-white text-amber-800',
    icon: <AlertTriangle className="h-5 w-5" />,
  },
  info: {
    wrapper: 'border-blue-200 bg-white text-blue-800',
    icon: <Info className="h-5 w-5" />,
  },
}

export function ToastProvider({ children }: PropsWithChildren) {
  const [items, setItems] = useState<ToastItem[]>([])

  const push = useCallback((type: ToastItem['type'], message: string) => {
    const id = crypto.randomUUID()
    setItems((current) => [...current, { id, type, message }])
    window.setTimeout(() => {
      setItems((current) => current.filter((item) => item.id !== id))
    }, 6000)
  }, [])

  const value = useMemo<ToastContextValue>(
    () => ({
      toast: {
        success: (message) => push('success', message),
        error: (message) => push('error', message),
        warning: (message) => push('warning', message),
        info: (message) => push('info', message),
      },
    }),
    [push],
  )

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="pointer-events-none fixed bottom-4 right-4 z-[60] flex w-full max-w-sm flex-col gap-3">
        {items.map((item) => (
          <div
            key={item.id}
            className={cn(
              'pointer-events-auto animate-slideInRight rounded-2xl border px-4 py-3 shadow-lg',
              toastStyles[item.type].wrapper,
            )}
          >
            <div className="flex items-start gap-3">
              <div className="mt-0.5">{toastStyles[item.type].icon}</div>
              <p className="text-base font-medium">{item.message}</p>
            </div>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}
