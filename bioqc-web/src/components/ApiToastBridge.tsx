import { useEffect } from 'react'
import { useToast } from './ui'

export function ApiToastBridge() {
  const { toast } = useToast()

  useEffect(() => {
    const listener = (event: Event) => {
      const customEvent = event as CustomEvent<string>
      if (customEvent.detail) {
        toast.error(customEvent.detail)
      }
    }

    window.addEventListener('app:api-error', listener)
    return () => window.removeEventListener('app:api-error', listener)
  }, [toast])

  return null
}
