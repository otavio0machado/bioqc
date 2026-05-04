import { Search } from 'lucide-react'
import type { ComboboxOption } from '../../ui'
import { Card, Combobox, Input, Select } from '../../ui'
import { CATEGORIES, REAGENT_STATUS_OPTIONS, TEMPS } from './constants'
import type { ReagentSortMode, ReagentViewMode } from './utils'

interface ReagentsFiltersProps {
  category: string
  status: string
  searchTerm: string
  manufacturerFilter: string
  tempFilter: string
  alertsOnly: boolean
  sortMode: ReagentSortMode
  viewMode: ReagentViewMode
  manufacturerOptions: ComboboxOption[]
  hasActiveFilters: boolean
  onCategoryChange: (value: string) => void
  onStatusChange: (value: string) => void
  onSearchChange: (value: string) => void
  onManufacturerChange: (value: string) => void
  onTempChange: (value: string) => void
  onAlertsOnlyChange: (value: boolean) => void
  onSortModeChange: (value: ReagentSortMode) => void
  onToggleViewMode: () => void
  onClearFilters: () => void
}

export function ReagentsFilters({
  category,
  status,
  searchTerm,
  manufacturerFilter,
  tempFilter,
  alertsOnly,
  sortMode,
  viewMode,
  manufacturerOptions,
  hasActiveFilters,
  onCategoryChange,
  onStatusChange,
  onSearchChange,
  onManufacturerChange,
  onTempChange,
  onAlertsOnlyChange,
  onSortModeChange,
  onToggleViewMode,
  onClearFilters,
}: ReagentsFiltersProps) {
  return (
    <Card className="space-y-3">
      <div className="grid gap-3 md:grid-cols-2 lg:grid-cols-4">
        <div className="relative">
          <Input
            placeholder="Buscar reagente ou lote..."
            value={searchTerm}
            onChange={(event) => onSearchChange(event.target.value)}
          />
          <Search className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-neutral-400" />
        </div>
        <Select value={category} onChange={(event) => onCategoryChange(event.target.value)}>
          <option value="">Todas categorias</option>
          {CATEGORIES.map((item) => (
            <option key={item} value={item}>
              {item}
            </option>
          ))}
        </Select>
        <Combobox
          placeholder="Todos fabricantes"
          value={manufacturerFilter}
          onChange={onManufacturerChange}
          options={manufacturerOptions}
          allowCustom={false}
          emptyText="Nenhum fabricante cadastrado"
        />
        <Select value={tempFilter} onChange={(event) => onTempChange(event.target.value)}>
          <option value="">Todas temperaturas</option>
          {TEMPS.map((item) => (
            <option key={item} value={item}>
              {item}
            </option>
          ))}
        </Select>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <Select
          value={status}
          onChange={(event) => onStatusChange(event.target.value)}
          title="Em estoque: cadastrado e ainda nao aberto. Em uso: aberto. Fora de estoque: estoque zerado dentro da validade. Vencido: validade ultrapassada."
        >
          <option value="">Todos status</option>
          {REAGENT_STATUS_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </Select>
        <label className="inline-flex cursor-pointer items-center gap-2 rounded-full border border-neutral-200 bg-white px-3 py-1.5 text-sm text-neutral-700">
          <input
            type="checkbox"
            checked={alertsOnly}
            onChange={(event) => onAlertsOnlyChange(event.target.checked)}
            className="h-4 w-4 rounded"
          />
          Somente alertas
        </label>
        <div className="ml-auto flex items-center gap-2 text-sm text-neutral-500">
          <span>Ordenar:</span>
          <select
            value={sortMode}
            onChange={(event) => onSortModeChange(event.target.value as ReagentSortMode)}
            className="rounded-lg border border-neutral-200 bg-white px-2 py-1 text-sm text-neutral-700"
          >
            <option value="urgency">Urgência</option>
            <option value="name">Nome</option>
            <option value="stock">Estoque atual</option>
          </select>
          <span className="mx-2 hidden h-4 w-px bg-neutral-200 sm:block" />
          <button
            type="button"
            onClick={onToggleViewMode}
            className="rounded-full border border-neutral-200 px-3 py-1 text-sm text-neutral-600 hover:bg-neutral-50"
            title="Alternar visualização"
          >
            {viewMode === 'tags' ? 'Ver lista' : 'Ver etiquetas'}
          </button>
        </div>
        {hasActiveFilters ? (
          <button type="button" onClick={onClearFilters} className="text-sm text-green-700 underline">
            Limpar filtros
          </button>
        ) : null}
      </div>
    </Card>
  )
}
