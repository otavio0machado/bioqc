import { X } from 'lucide-react'
import {
  useCallback,
  useEffect,
  useRef,
  type KeyboardEvent as ReactKeyboardEvent,
  type ReactNode,
} from 'react'
import { createPortal } from 'react-dom'
import { cn } from '../../utils/cn'

interface ModalProps {
  isOpen: boolean
  onClose: () => void
  title: string
  children: ReactNode
  footer?: ReactNode
  size?: 'sm' | 'md' | 'lg'
}

const sizeClasses = {
  sm: 'max-w-md',
  md: 'max-w-2xl',
  lg: 'max-w-4xl',
}

export function Modal({ isOpen, onClose, title, children, footer, size = 'md' }: ModalProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const onCloseRef = useRef(onClose)
  const wasOpenRef = useRef(false)

  useEffect(() => {
    onCloseRef.current = onClose
  }, [onClose])

  const stableOnClose = useCallback(() => onCloseRef.current(), [])

  useEffect(() => {
    if (!isOpen) {
      wasOpenRef.current = false
      return
    }

    if (!wasOpenRef.current) {
      wasOpenRef.current = true
      const firstInput = containerRef.current?.querySelector<HTMLElement>(
        'input, select, textarea',
      )
      if (firstInput) {
        firstInput.focus()
      } else {
        const focusable = containerRef.current?.querySelector<HTMLElement>(
          'button, [href], [tabindex]:not([tabindex="-1"])',
        )
        focusable?.focus()
      }
    }

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onCloseRef.current()
      }
      if (event.key === 'Tab' && containerRef.current) {
        const nodes = Array.from(
          containerRef.current.querySelectorAll<HTMLElement>(
            'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
          ),
        ).filter((node) => !node.hasAttribute('disabled'))
        if (nodes.length === 0) {
          return
        }
        const first = nodes[0]
        const last = nodes[nodes.length - 1]
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault()
          last.focus()
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault()
          first.focus()
        }
      }
    }

    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [isOpen])

  if (!isOpen) {
    return null
  }

  return createPortal(
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4 backdrop-blur-sm"
      onClick={stableOnClose}
      aria-modal="true"
      role="dialog"
    >
      <div
        ref={containerRef}
        className={cn(
          'animate-slideUp overflow-hidden rounded-3xl bg-white shadow-modal',
          'max-h-[90vh] w-full',
          sizeClasses[size],
        )}
        onClick={(event) => event.stopPropagation()}
        onKeyDown={(event: ReactKeyboardEvent<HTMLDivElement>) => event.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-neutral-200 px-6 py-4">
          <h2 className="text-lg font-semibold text-neutral-900">{title}</h2>
          <button
            type="button"
            className="rounded-full p-2 text-neutral-500 transition hover:bg-neutral-100 hover:text-neutral-900"
            onClick={stableOnClose}
            aria-label="Fechar modal"
          >
            <X className="h-5 w-5" />
          </button>
        </div>
        <div className="max-h-[calc(90vh-9rem)] overflow-y-auto px-6 py-5">{children}</div>
        {footer ? <div className="border-t border-neutral-200 px-6 py-4">{footer}</div> : null}
      </div>
    </div>,
    document.body,
  )
}
