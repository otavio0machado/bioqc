import { Check, ChevronDown, Plus, Search } from 'lucide-react'
import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { cn } from '../../utils/cn'

export interface ComboboxOption {
  value: string
  label: string
  description?: string
}

interface ComboboxProps {
  label?: string
  placeholder?: string
  error?: string
  value: string
  onChange: (value: string) => void
  options: ComboboxOption[]
  /** Quando true, permite digitar um valor novo (nao presente na lista). */
  allowCustom?: boolean
  /** Label do item virtual "Criar novo". Default: 'Criar novo'. */
  createLabel?: string
  /** Mensagem quando nao ha resultados. */
  emptyText?: string
  disabled?: boolean
  icon?: ReactNode
  id?: string
  name?: string
}

/** Remove acentos e normaliza para minusculas, usado em busca insensivel. */
function normalize(text: string): string {
  return text
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .trim()
}

/**
 * Combobox com busca, opcao de criar item novo e navegacao por teclado.
 *
 * Usado como padrao unico para selecao com autocomplete em todo o sistema
 * (RegistroTab exame, ReferenciasTab nome do registro, filtros, etc).
 */
export function Combobox({
  label,
  placeholder = 'Selecione ou digite...',
  error,
  value,
  onChange,
  options = [],
  allowCustom = true,
  createLabel = 'Criar novo',
  emptyText = 'Nenhum resultado encontrado',
  disabled,
  icon,
  id,
  name,
}: ComboboxProps) {
  const [isOpen, setIsOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [highlightedIndex, setHighlightedIndex] = useState(0)
  const inputRef = useRef<HTMLInputElement>(null)
  const listRef = useRef<HTMLUListElement>(null)
  const rootRef = useRef<HTMLDivElement>(null)
  const fieldId = id ?? name

  const filtered = useMemo(() => {
    const q = normalize(query)
    if (!q) return options
    return options.filter((o) => normalize(o.label).includes(q) || normalize(o.value).includes(q))
  }, [options, query])

  const hasExact = useMemo(() => {
    const q = normalize(query)
    if (!q) return true
    return options.some((o) => normalize(o.label) === q || normalize(o.value) === q)
  }, [options, query])

  const showCreateEntry = allowCustom && query.trim().length > 0 && !hasExact

  useEffect(() => {
    if (!isOpen) return
    function handleClick(event: MouseEvent) {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setIsOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [isOpen])

  useEffect(() => {
    setHighlightedIndex(0)
  }, [query, isOpen])

  const selectedLabel = useMemo(() => {
    const hit = options.find((o) => o.value === value)
    return hit?.label ?? value
  }, [options, value])

  function handleSelect(next: string) {
    onChange(next)
    setQuery('')
    setIsOpen(false)
    inputRef.current?.blur()
  }

  function handleKeyDown(event: React.KeyboardEvent<HTMLInputElement>) {
    const total = filtered.length + (showCreateEntry ? 1 : 0)
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      if (!isOpen) setIsOpen(true)
      setHighlightedIndex((i) => (total === 0 ? 0 : (i + 1) % total))
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      if (!isOpen) setIsOpen(true)
      setHighlightedIndex((i) => (total === 0 ? 0 : (i - 1 + total) % total))
    } else if (event.key === 'Enter') {
      event.preventDefault()
      if (!isOpen) {
        setIsOpen(true)
        return
      }
      if (highlightedIndex < filtered.length) {
        handleSelect(filtered[highlightedIndex].value)
      } else if (showCreateEntry) {
        handleSelect(query.trim())
      }
    } else if (event.key === 'Escape') {
      setIsOpen(false)
    }
  }

  return (
    <div className="block space-y-1" ref={rootRef}>
      {label ? (
        <label className="text-base font-medium text-neutral-700" htmlFor={fieldId}>
          {label}
        </label>
      ) : null}
      <div
        className={cn(
          'relative flex items-center gap-3 rounded-xl border bg-white px-4 py-3 transition focus-within:ring-2',
          error
            ? 'border-red-500 focus-within:border-red-500 focus-within:ring-red-500/20'
            : 'border-neutral-200 focus-within:border-green-800 focus-within:ring-green-800/20',
          disabled ? 'opacity-60' : '',
        )}
      >
        <span className="text-neutral-400">{icon ?? <Search className="h-4 w-4" />}</span>
        <input
          ref={inputRef}
          id={fieldId}
          name={name}
          type="text"
          disabled={disabled}
          placeholder={placeholder}
          value={isOpen ? query : selectedLabel}
          onChange={(event) => {
            const raw = event.target.value
            setQuery(raw)
            if (!isOpen) setIsOpen(true)
            if (allowCustom) {
              // QA P0: ao emitir "criar novo", envia o valor trimado para evitar
              // trailing whitespace que contaminaria o campo do formulario.
              onChange(raw.trim())
            }
          }}
          onFocus={() => setIsOpen(true)}
          onKeyDown={handleKeyDown}
          className="w-full border-none bg-transparent text-base outline-none placeholder:text-neutral-400"
          autoComplete="off"
          role="combobox"
          aria-expanded={isOpen}
          aria-controls={fieldId ? `${fieldId}-listbox` : undefined}
        />
        <button
          type="button"
          tabIndex={-1}
          onClick={() => {
            if (disabled) return
            setIsOpen((open) => !open)
            inputRef.current?.focus()
          }}
          className="text-neutral-400 hover:text-neutral-600"
          aria-label="Abrir opcoes"
        >
          <ChevronDown className={cn('h-4 w-4 transition', isOpen ? 'rotate-180' : '')} />
        </button>
        {isOpen ? (
          <ul
            ref={listRef}
            id={fieldId ? `${fieldId}-listbox` : undefined}
            role="listbox"
            className="absolute left-0 right-0 top-full z-30 mt-1 max-h-72 overflow-auto rounded-xl border border-neutral-200 bg-white shadow-lg"
          >
            {filtered.length === 0 && !showCreateEntry ? (
              <li className="px-4 py-3 text-sm text-neutral-400">{emptyText}</li>
            ) : null}
            {filtered.map((option, index) => {
              const isSelected = option.value === value
              const isHighlighted = index === highlightedIndex
              return (
                <li
                  key={option.value}
                  role="option"
                  aria-selected={isSelected}
                  onMouseDown={(event) => {
                    event.preventDefault()
                    handleSelect(option.value)
                  }}
                  onMouseEnter={() => setHighlightedIndex(index)}
                  className={cn(
                    'flex cursor-pointer items-center justify-between gap-3 px-4 py-2.5 text-sm',
                    isHighlighted ? 'bg-green-50 text-green-900' : 'text-neutral-700',
                  )}
                >
                  <div className="min-w-0">
                    <div className="truncate font-medium">{option.label}</div>
                    {option.description ? (
                      <div className="truncate text-xs text-neutral-500">{option.description}</div>
                    ) : null}
                  </div>
                  {isSelected ? <Check className="h-4 w-4 text-green-700" /> : null}
                </li>
              )
            })}
            {showCreateEntry ? (
              <li
                role="option"
                aria-selected={highlightedIndex === filtered.length}
                onMouseDown={(event) => {
                  event.preventDefault()
                  handleSelect(query.trim())
                }}
                onMouseEnter={() => setHighlightedIndex(filtered.length)}
                className={cn(
                  'flex cursor-pointer items-center gap-2 border-t border-neutral-100 px-4 py-2.5 text-sm font-medium',
                  highlightedIndex === filtered.length ? 'bg-green-50 text-green-900' : 'text-green-800',
                )}
              >
                <Plus className="h-4 w-4" />
                {createLabel}: &quot;{query.trim()}&quot;
              </li>
            ) : null}
          </ul>
        ) : null}
      </div>
      {error ? <p className="text-sm text-red-500">{error}</p> : null}
    </div>
  )
}
