import { useMemo } from 'react'
import type {
  ReportFilterField,
  ReportFilterSpec,
} from '../../types/reportsV2'
import { Combobox, Input, Select, TextArea } from '../ui'
import { useEquipmentSuggestions } from '../../hooks/useReportsV2'

interface DynamicFilterFormProps {
  filterSpec: ReportFilterSpec
  values: Record<string, unknown>
  onChange: (values: Record<string, unknown>) => void
  errors?: Record<string, string>
}

/**
 * Renderiza um formulario dinamico a partir do {@link ReportFilterSpec}.
 *
 * <p>Regras aplicadas:
 * <ul>
 *   <li>DATE_RANGE esta no contrato mas o unico relatorio F1 usa DATE + DATE
 *       para dateFrom/dateTo - quando aparecer DATE_RANGE tratamos como dois
 *       inputs geminados no proprio campo.</li>
 *   <li>INTEGER permite min/max quando a UI reconhece o key (month=1..12,
 *       year=2000..2100); demais ficam livres e validam apenas como numero.</li>
 *   <li>UUID_LIST sem combobox disponivel de dados => textarea com UUIDs
 *       separados por virgula (consistente com contrato backend que aceita
 *       list&lt;UUID&gt; serializada).</li>
 * </ul>
 *
 * <p>O componente nao faz debounce - quem consome (ReportBuilder) decide
 * quando disparar preview/generate para evitar flooding.
 */
export function DynamicFilterForm({
  filterSpec,
  values,
  onChange,
  errors,
}: DynamicFilterFormProps) {
  const fields = filterSpec.fields ?? []

  const handleChange = (key: string, value: unknown) => {
    const next = { ...values }
    if (value === undefined || value === '' || value === null) {
      delete next[key]
    } else {
      next[key] = value
    }
    onChange(next)
  }

  return (
    <div className="space-y-4">
      {fields.map((field) => (
        <FilterFieldRenderer
          key={field.key}
          field={field}
          value={values[field.key]}
          onChange={(value) => handleChange(field.key, value)}
          error={errors?.[field.key]}
        />
      ))}
    </div>
  )
}

interface FilterFieldRendererProps {
  field: ReportFilterField
  value: unknown
  onChange: (value: unknown) => void
  error?: string
}

function FilterFieldRenderer({ field, value, onChange, error }: FilterFieldRendererProps) {
  const labelText = field.required ? `${field.label} *` : field.label
  const helpText = field.helpText ?? undefined

  switch (field.type) {
    case 'STRING_ENUM': {
      const options = field.allowedValues ?? []
      return (
        <div className="space-y-1">
          <Select
            label={labelText}
            value={typeof value === 'string' ? value : ''}
            onChange={(event) => onChange(event.target.value || undefined)}
            error={error}
          >
            <option value="">Selecione...</option>
            {options.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </Select>
          {helpText ? <p className="text-xs text-neutral-500">{helpText}</p> : null}
        </div>
      )
    }

    case 'INTEGER': {
      const bounds = getIntegerBounds(field.key)
      return (
        <div className="space-y-1">
          <Input
            label={labelText}
            type="number"
            inputMode="numeric"
            min={bounds?.min}
            max={bounds?.max}
            step={1}
            value={value == null ? '' : String(value)}
            onChange={(event) => {
              const raw = event.target.value
              if (raw === '') {
                onChange(undefined)
                return
              }
              const parsed = Number.parseInt(raw, 10)
              onChange(Number.isFinite(parsed) ? parsed : undefined)
            }}
            error={error}
          />
          {helpText ? <p className="text-xs text-neutral-500">{helpText}</p> : null}
        </div>
      )
    }

    case 'DATE': {
      return (
        <div className="space-y-1">
          <Input
            label={labelText}
            type="date"
            value={typeof value === 'string' ? value : ''}
            onChange={(event) => onChange(event.target.value || undefined)}
            error={error}
          />
          {helpText ? <p className="text-xs text-neutral-500">{helpText}</p> : null}
        </div>
      )
    }

    case 'DATE_RANGE':
      return (
        <DateRangeField
          field={field}
          value={value}
          onChange={onChange}
          error={error}
          labelText={labelText}
          helpText={helpText}
        />
      )

    case 'UUID':
      return (
        <div className="space-y-1">
          <Input
            label={labelText}
            type="text"
            placeholder="uuid v4"
            value={typeof value === 'string' ? value : ''}
            onChange={(event) => onChange(event.target.value || undefined)}
            error={error}
          />
          {helpText ? <p className="text-xs text-neutral-500">{helpText}</p> : null}
        </div>
      )

    case 'UUID_LIST':
      return (
        <UuidListField
          value={value}
          onChange={onChange}
          error={error}
          labelText={labelText}
          helpText={helpText}
        />
      )

    case 'BOOLEAN':
      return (
        <div className="space-y-1">
          <label className="flex items-center gap-2 text-base font-medium text-neutral-700">
            <input
              type="checkbox"
              checked={Boolean(value)}
              onChange={(event) => onChange(event.target.checked)}
              className="h-4 w-4 rounded border-neutral-300 text-green-800 focus:ring-green-800"
            />
            {labelText}
          </label>
          {helpText ? <p className="text-xs text-neutral-500">{helpText}</p> : null}
          {error ? <p className="text-sm text-red-500">{error}</p> : null}
        </div>
      )

    case 'STRING_ENUM_MULTI': {
      const options = field.allowedValues ?? []
      const selected = Array.isArray(value) ? (value as string[]) : []
      const toggle = (opt: string) => {
        const next = selected.includes(opt)
          ? selected.filter((v) => v !== opt)
          : [...selected, opt]
        onChange(next.length > 0 ? next : undefined)
      }
      return (
        <div className="space-y-1">
          <div className="text-base font-medium text-neutral-700">{labelText}</div>
          <div className="flex flex-wrap gap-2">
            {options.map((opt) => {
              const isActive = selected.includes(opt)
              return (
                <button
                  key={opt}
                  type="button"
                  onClick={() => toggle(opt)}
                  className={
                    'rounded-full border px-3 py-1.5 text-sm font-medium transition ' +
                    (isActive
                      ? 'border-green-800 bg-green-800 text-white'
                      : 'border-neutral-200 bg-white text-neutral-700 hover:border-neutral-300')
                  }
                  aria-pressed={isActive}
                >
                  {opt}
                </button>
              )
            })}
          </div>
          {helpText ? <p className="text-xs text-neutral-500">{helpText}</p> : null}
          {error ? <p className="text-sm text-red-500">{error}</p> : null}
        </div>
      )
    }

    case 'STRING':
      if (field.key === 'equipment') {
        return (
          <EquipmentField
            field={field}
            value={value}
            onChange={onChange}
            error={error}
            labelText={labelText}
            helpText={helpText}
          />
        )
      }
      return (
        <div className="space-y-1">
          <Input
            label={labelText}
            value={typeof value === 'string' ? value : ''}
            onChange={(event) => onChange(event.target.value || undefined)}
            error={error}
          />
          {helpText ? <p className="text-xs text-neutral-500">{helpText}</p> : null}
        </div>
      )

    default:
      // Exaustividade defensiva - tipo desconhecido vira input text.
      return (
        <div className="space-y-1">
          <Input
            label={labelText}
            value={typeof value === 'string' ? value : ''}
            onChange={(event) => onChange(event.target.value || undefined)}
            error={error}
          />
          {helpText ? <p className="text-xs text-neutral-500">{helpText}</p> : null}
        </div>
      )
  }
}

interface DateRangeValue {
  dateFrom?: string
  dateTo?: string
}

interface DateRangeFieldProps {
  field: ReportFilterField
  value: unknown
  onChange: (value: unknown) => void
  error?: string
  labelText: string
  helpText?: string
}

function DateRangeField({ value, onChange, error, labelText, helpText }: DateRangeFieldProps) {
  const range = (value && typeof value === 'object' ? value : {}) as DateRangeValue
  const from = range.dateFrom ?? ''
  const to = range.dateTo ?? ''

  const rangeError = useMemo(() => {
    if (error) return error
    if (from && to && to < from) return 'Data final deve ser maior ou igual a inicial.'
    return undefined
  }, [error, from, to])

  return (
    <div className="space-y-1">
      <div className="text-base font-medium text-neutral-700">{labelText}</div>
      <div className="grid gap-3 sm:grid-cols-2">
        <Input
          type="date"
          value={from}
          onChange={(event) => {
            const next = event.target.value || undefined
            onChange(next || to ? { dateFrom: next, dateTo: to || undefined } : undefined)
          }}
          label="De"
        />
        <Input
          type="date"
          value={to}
          onChange={(event) => {
            const next = event.target.value || undefined
            onChange(from || next ? { dateFrom: from || undefined, dateTo: next } : undefined)
          }}
          label="Ate"
        />
      </div>
      {helpText ? <p className="text-xs text-neutral-500">{helpText}</p> : null}
      {rangeError ? <p className="text-sm text-red-500">{rangeError}</p> : null}
    </div>
  )
}

interface UuidListFieldProps {
  value: unknown
  onChange: (value: unknown) => void
  error?: string
  labelText: string
  helpText?: string
}

function UuidListField({ value, onChange, error, labelText, helpText }: UuidListFieldProps) {
  const asString = Array.isArray(value) ? (value as string[]).join(', ') : ''
  return (
    <div className="space-y-1">
      <TextArea
        label={labelText}
        placeholder="uuid1, uuid2, uuid3"
        value={asString}
        onChange={(event) => {
          const raw = event.target.value
          if (!raw.trim()) {
            onChange(undefined)
            return
          }
          const list = raw
            .split(/[\s,;]+/)
            .map((item) => item.trim())
            .filter(Boolean)
          onChange(list.length > 0 ? list : undefined)
        }}
        error={error}
      />
      {helpText ? <p className="text-xs text-neutral-500">{helpText}</p> : null}
    </div>
  )
}

interface EquipmentFieldProps {
  field: ReportFilterField
  value: unknown
  onChange: (value: unknown) => void
  error?: string
  labelText: string
  helpText?: string
}

function EquipmentField({ value, onChange, error, labelText, helpText }: EquipmentFieldProps) {
  const { data: suggestions = [] } = useEquipmentSuggestions()
  const options = suggestions.map((item) => ({ value: item, label: item }))
  return (
    <div className="space-y-1">
      <Combobox
        label={labelText}
        value={typeof value === 'string' ? value : ''}
        onChange={(next) => onChange(next || undefined)}
        options={options}
        allowCustom
        placeholder="Ex: Vitros, Cobas..."
      />
      {helpText ? <p className="text-xs text-neutral-500">{helpText}</p> : null}
      {error ? <p className="text-sm text-red-500">{error}</p> : null}
    </div>
  )
}

/**
 * Convencoes pt-BR para campos INTEGER conhecidos por nome. Mantem o
 * formulario fiel ao dominio (mes 1..12, ano 2000..2100) sem duplicar
 * regras de negocio - o backend continua sendo a autoridade final.
 */
function getIntegerBounds(key: string): { min?: number; max?: number } | undefined {
  switch (key) {
    case 'month':
      return { min: 1, max: 12 }
    case 'year':
      return { min: 2000, max: 2100 }
    default:
      return undefined
  }
}
