import { AlertTriangle, ArrowDownLeft, ArrowUpRight, ChevronDown, ChevronRight, Pencil } from 'lucide-react'
import { useMemo, useState, type Dispatch, type SetStateAction } from 'react'
import type {
  ReagentLabelSummary,
  ReagentLot,
  ReagentLotRequest,
  StockMovement,
  StockMovementRequest,
} from '../../../types'
import { cn } from '../../../utils/cn'
import { todayLocal } from '../../../utils/date'
import { Button, Combobox, Input, Modal, Select, type ComboboxOption } from '../../ui'
import {
  CATEGORIES,
  MOVEMENT_REASONS,
  MOVEMENT_TYPE_OPTIONS,
  TEMPS,
} from './constants'
import { canReceiveEntry } from './utils'

interface ReagentLotModalProps {
  form: ReagentLotRequest
  isOpen: boolean
  isEditing: boolean
  isSaving: boolean
  labels: ReagentLabelSummary[]
  manufacturerOptions?: ComboboxOption[]
  locationOptions?: ComboboxOption[]
  supplierOptions?: ComboboxOption[]
  onClose: () => void
  onSave: () => void
  setForm: Dispatch<SetStateAction<ReagentLotRequest>>
}

/**
 * Modal de cadastro/edicao de lote pos-refator v3.1.
 *
 * Layout canonico:
 * - Secao 1 (Identificacao): Etiqueta (combobox), Lote, Fabricante, Categoria (combobox fechado).
 * - Secao 2 (Estoque & Validade): Em estoque, Em uso, Validade.
 * - Secao 3 (Armazenamento): Localizacao, Temperatura.
 * - Secao 4 (Detalhes adicionais — colapsavel): Fornecedor, Recebimento.
 *
 * Mudancas v3.1:
 * - Drop campo "Data de abertura": {@code openedDate} passa a ser sempre gravada
 *   via popup do botao "Abrir unidade" (movimento ABERTURA com {@code eventDate}).
 * - Drop select "Status": status e derivado pelo backend (validade x estoque x abertura).
 *   Para arquivar, usar botao "Arquivar" do card. O state {@code form.status} ainda
 *   existe para compatibilidade do request, mas no CREATE manda sempre 'em_estoque'
 *   (default) e no UPDATE o backend rejeita 'inativo'. UI nao expoe mais escolha.
 * - Categoria vira {@code Combobox} {@code allowCustom=false} alinhado com
 *   Etiqueta/Fabricante/Localizacao/Fornecedor (search-as-you-type).
 *
 * Comportamentos chave preservados:
 * - Banner amarelo quando {@code expiryDate < hoje}: o backend forca status='vencido'.
 * - Trim defensivo no submit (bloqueante audit 4.2.1) — feito via service tambem.
 */
export function ReagentLotModal({
  form,
  isOpen,
  isEditing,
  isSaving,
  labels,
  manufacturerOptions = [],
  locationOptions = [],
  supplierOptions = [],
  onClose,
  onSave,
  setForm,
}: ReagentLotModalProps) {
  const [showAdditional, setShowAdditional] = useState(false)
  const today = todayLocal()
  const expiryWillForceVencido = Boolean(form.expiryDate) && form.expiryDate < today

  const labelOptions = useMemo<ComboboxOption[]>(
    () =>
      labels
        .map((summary) => ({
          value: summary.label,
          label: summary.label,
          description:
            summary.total > 1 ? `${summary.total} lotes cadastrados` : '1 lote cadastrado',
        }))
        .sort((a, b) => a.label.localeCompare(b.label)),
    [labels],
  )

  const categoryOptions = useMemo<ComboboxOption[]>(
    () => CATEGORIES.map((category) => ({ value: category, label: category })),
    [],
  )

  const handleSave = () => {
    // Bloqueante audit 4.2.1: garante trim no submit, mesmo que o usuario tenha
    // digitado whitespace antes/depois. Service tambem sanitiza antes do POST.
    setForm((current) => ({
      ...current,
      label: current.label?.trim() ?? '',
      lotNumber: current.lotNumber?.trim() ?? '',
      manufacturer: current.manufacturer?.trim() ?? '',
      location: current.location?.trim() ?? '',
      supplier: current.supplier?.trim() || undefined,
    }))
    onSave()
  }

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={isEditing ? 'Editar Lote' : 'Novo Lote'}
      size="lg"
      footer={
        <div className="flex justify-end gap-3">
          <Button variant="ghost" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={handleSave} loading={isSaving}>
            {isEditing ? 'Atualizar' : 'Cadastrar'}
          </Button>
        </div>
      }
    >
      <div className="space-y-5">
        <FormSection title="Identificação">
          <div className="grid gap-3 sm:grid-cols-2">
            <Combobox
              id="reagent-label-input"
              label="Etiqueta *"
              placeholder="Buscar ou criar etiqueta..."
              value={form.label ?? ''}
              onChange={(value) =>
                setForm((current) => ({ ...current, label: value }))
              }
              options={labelOptions}
              allowCustom
              createLabel="+ Criar nova etiqueta"
              emptyText="Nenhuma etiqueta cadastrada"
            />
            <Input
              label="Nº do Lote *"
              value={form.lotNumber}
              onChange={(event) =>
                setForm((current) => ({ ...current, lotNumber: event.target.value }))
              }
            />
            <Combobox
              id="reagent-manufacturer-input"
              label="Fabricante *"
              placeholder="Buscar ou criar fabricante..."
              value={form.manufacturer ?? ''}
              onChange={(value) =>
                setForm((current) => ({ ...current, manufacturer: value }))
              }
              options={manufacturerOptions}
              allowCustom
              createLabel="+ Criar novo fabricante"
              emptyText="Nenhum fabricante cadastrado"
            />
            <Combobox
              id="reagent-category-input"
              label="Categoria *"
              placeholder="Buscar categoria..."
              value={form.category ?? ''}
              onChange={(value) =>
                setForm((current) => ({ ...current, category: value }))
              }
              options={categoryOptions}
              allowCustom={false}
              emptyText="Nenhuma categoria encontrada"
            />
          </div>
        </FormSection>

        <FormSection title="Estoque & Validade">
          <div className="grid gap-3 sm:grid-cols-3">
            <div>
              <Input
                label="Entrada *"
                type="number"
                min="0"
                step="1"
                value={String(form.unitsInStock ?? 0)}
                onChange={(event) =>
                  setForm((current) => ({
                    ...current,
                    unitsInStock: Math.max(0, Math.floor(Number(event.target.value || 0))),
                  }))
                }
              />
              <p className="mt-1 text-xs text-neutral-500">
                Quantidade recebida — unidades fechadas, prontas para abrir.
              </p>
            </div>
            <div>
              <Input
                label="Em uso *"
                type="number"
                min="0"
                step="1"
                value={String(form.unitsInUse ?? 0)}
                onChange={(event) =>
                  setForm((current) => ({
                    ...current,
                    unitsInUse: Math.max(0, Math.floor(Number(event.target.value || 0))),
                  }))
                }
              />
              <p className="mt-1 text-xs text-neutral-500">
                Unidades já abertas, sendo consumidas.
              </p>
            </div>
            <Input
              label="Validade *"
              type="date"
              value={form.expiryDate}
              onChange={(event) =>
                setForm((current) => ({ ...current, expiryDate: event.target.value }))
              }
            />
          </div>
          <div className="mt-2 text-xs text-neutral-500">
            Total: <strong>{(form.unitsInStock ?? 0) + (form.unitsInUse ?? 0)}</strong> unidade(s)
          </div>
          {expiryWillForceVencido ? (
            <div className="mt-3 flex items-start gap-2 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
              <span>
                Este lote será salvo como <strong>Vencido</strong> automaticamente porque a validade já
                passou. O servidor sobrescreve o status enviado.
              </span>
            </div>
          ) : null}
        </FormSection>

        <FormSection title="Armazenamento">
          <div className="grid gap-3 sm:grid-cols-2">
            <Combobox
              id="reagent-location-input"
              label="Localização *"
              placeholder="Buscar ou criar localização..."
              value={form.location ?? ''}
              onChange={(value) =>
                setForm((current) => ({ ...current, location: value }))
              }
              options={locationOptions}
              allowCustom
              createLabel="+ Criar nova localização"
              emptyText="Nenhuma localização cadastrada"
            />
            <Select
              label="Temperatura *"
              value={form.storageTemp ?? ''}
              onChange={(event) =>
                setForm((current) => ({ ...current, storageTemp: event.target.value }))
              }
            >
              <option value="">Selecione...</option>
              {TEMPS.map((temp) => (
                <option key={temp} value={temp}>
                  {temp}
                </option>
              ))}
            </Select>
          </div>
        </FormSection>

        <div>
          <button
            type="button"
            onClick={() => setShowAdditional((current) => !current)}
            className={cn(
              'flex w-full items-center justify-between rounded-xl border border-neutral-200 bg-white px-4 py-3 text-sm font-medium text-neutral-700 transition hover:border-green-300',
              showAdditional ? 'border-green-300 bg-green-50/40 text-green-900' : '',
            )}
            aria-expanded={showAdditional}
          >
            <span className="flex items-center gap-2">
              {showAdditional ? (
                <ChevronDown className="h-4 w-4" />
              ) : (
                <ChevronRight className="h-4 w-4" />
              )}
              Detalhes adicionais (opcional)
            </span>
            <span className="text-xs text-neutral-500">
              Fornecedor e recebimento
            </span>
          </button>
          {showAdditional ? (
            <div className="mt-3 grid gap-3 rounded-xl border border-neutral-200 bg-neutral-50 p-4 sm:grid-cols-2">
              <Combobox
                id="reagent-supplier-input"
                label="Fornecedor"
                placeholder="Buscar ou criar fornecedor..."
                value={form.supplier ?? ''}
                onChange={(value) =>
                  setForm((current) => ({ ...current, supplier: value || undefined }))
                }
                options={supplierOptions}
                allowCustom
                createLabel="+ Criar novo fornecedor"
                emptyText="Nenhum fornecedor cadastrado"
              />
              <Input
                label="Data de recebimento"
                type="date"
                value={form.receivedDate ?? ''}
                onChange={(event) =>
                  setForm((current) => ({
                    ...current,
                    receivedDate: event.target.value || undefined,
                  }))
                }
              />
            </div>
          ) : null}
        </div>
      </div>
    </Modal>
  )
}

interface ReagentMovementModalProps {
  form: StockMovementRequest
  isOpen: boolean
  isSaving: boolean
  lot: ReagentLot | null
  onClose: () => void
  onSave: () => void
  setForm: Dispatch<SetStateAction<StockMovementRequest>>
  movements: StockMovement[]
  /**
   * Quando {@code true}, o usuario abriu o modal explicitamente para uma operacao
   * de ENTRADA — o select de tipo fica fixado e a UI deixa claro o contexto.
   */
  lockType?: boolean
}

export function ReagentMovementModal({
  form,
  isOpen,
  isSaving,
  lot,
  onClose,
  onSave,
  setForm,
  movements,
  lockType = false,
}: ReagentMovementModalProps) {
  const canUseEntrada = lot ? canReceiveEntry(lot) : true
  const today = todayLocal()

  const titleSuffix = lot ? ` · Lote ${lot.lotNumber}` : ''
  // Refator v3.1: quando o usuario abriu via "Final de Uso", o titulo
  // reflete a operacao alvo. Caso contrario fica "Movimentação".
  const titlePrefix =
    lockType && form.type === 'CONSUMO' ? 'Final de Uso' : 'Movimentação'
  const isConsumoLocked = lockType && form.type === 'CONSUMO'

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={`${titlePrefix}${titleSuffix}`}
      footer={
        <div className="flex justify-end gap-3">
          <Button variant="ghost" onClick={onClose}>
            Fechar
          </Button>
          <Button onClick={onSave} loading={isSaving}>
            Registrar
          </Button>
        </div>
      }
    >
      <div className="grid gap-3 sm:grid-cols-2">
        <Select
          label="Tipo"
          value={form.type}
          disabled={lockType}
          onChange={(event) =>
            setForm((current) => ({
              ...current,
              type: event.target.value as StockMovementRequest['type'],
            }))
          }
        >
          {MOVEMENT_TYPE_OPTIONS.map((option) => {
            const disabled = option.value === 'ENTRADA' && !canUseEntrada
            return (
              <option key={option.value} value={option.value} disabled={disabled}>
                {option.label} — {option.hint}
              </option>
            )
          })}
        </Select>
        {form.type === 'AJUSTE' ? (
          <div className="grid gap-2 sm:col-span-1">
            <Input
              label="Em estoque (alvo) *"
              type="number"
              min="0"
              step="1"
              value={String(form.targetUnitsInStock ?? 0)}
              onChange={(event) =>
                setForm((current) => ({
                  ...current,
                  targetUnitsInStock: Math.max(0, Math.floor(Number(event.target.value || 0))),
                }))
              }
            />
            <Input
              label="Em uso (alvo) *"
              type="number"
              min="0"
              step="1"
              value={String(form.targetUnitsInUse ?? 0)}
              onChange={(event) =>
                setForm((current) => ({
                  ...current,
                  targetUnitsInUse: Math.max(0, Math.floor(Number(event.target.value || 0))),
                }))
              }
            />
          </div>
        ) : (
          <Input
            label="Quantidade *"
            type="number"
            min={form.type === 'ABERTURA' || form.type === 'FECHAMENTO' ? 1 : 0}
            max={form.type === 'ABERTURA' || form.type === 'FECHAMENTO' ? 1 : undefined}
            value={String(form.quantity)}
            disabled={form.type === 'ABERTURA' || form.type === 'FECHAMENTO'}
            onChange={(event) =>
              setForm((current) => ({ ...current, quantity: Number(event.target.value) }))
            }
          />
        )}
        <Input
          label="Responsável *"
          value={form.responsible}
          onChange={(event) =>
            setForm((current) => ({ ...current, responsible: event.target.value }))
          }
        />
        <Select
          label={
            form.type === 'AJUSTE' ||
            (form.type === 'CONSUMO' && lot?.status === 'vencido')
              ? 'Motivo *'
              : 'Motivo (opcional)'
          }
          value={form.reason ?? ''}
          onChange={(event) =>
            setForm((current) => ({
              ...current,
              reason: (event.target.value || null) as StockMovementRequest['reason'],
            }))
          }
        >
          <option value="">
            {form.type === 'AJUSTE' || (form.type === 'CONSUMO' && lot?.status === 'vencido')
              ? 'Selecione o motivo'
              : 'Sem motivo específico'}
          </option>
          {MOVEMENT_REASONS.map((reason) => (
            <option key={reason.value} value={reason.value}>
              {reason.label}
            </option>
          ))}
        </Select>
        <Input
          label="Observações"
          value={form.notes ?? ''}
          onChange={(event) =>
            setForm((current) => ({ ...current, notes: event.target.value }))
          }
        />
        {isConsumoLocked ? (
          <div>
            <Input
              label="Data de fim de uso *"
              type="date"
              max={today}
              value={form.eventDate ?? today}
              onChange={(event) =>
                setForm((current) => ({
                  ...current,
                  eventDate: event.target.value || undefined,
                }))
              }
            />
            <p className="mt-1 text-xs text-neutral-500">
              Quando a unidade acabou de fato. Padrão é hoje.
            </p>
            {form.eventDate && form.eventDate > today ? (
              <p className="mt-1 text-xs font-medium text-red-700">
                A data não pode ser futura.
              </p>
            ) : null}
          </div>
        ) : null}
      </div>

      {(form.type === 'ABERTURA' || form.type === 'FECHAMENTO') ? (
        <p className="mt-2 text-xs text-neutral-500">
          Operação unitária — uma unidade por movimento.
        </p>
      ) : null}

      {form.type === 'AJUSTE' ? (
        <p className="mt-2 text-xs text-amber-700">
          Ajustes manuais exigem um motivo para auditoria.
        </p>
      ) : null}

      {form.type === 'CONSUMO' && lot?.status === 'vencido' ? (
        <p className="mt-2 text-xs text-amber-700">
          CONSUMO em lote vencido exige um motivo (descarte registrado).
        </p>
      ) : null}

      {!canUseEntrada ? (
        <p className="mt-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs font-medium text-amber-800">
          {lot?.movementWarning ??
            'Este lote não aceita ENTRADA (vencido ou inativo). Use AJUSTE ou reative o lote.'}
        </p>
      ) : null}

      {movements.length > 0 ? (
        <div className="mt-4 border-t border-neutral-200 pt-4">
          <div className="mb-2 flex items-center justify-between text-sm font-medium text-neutral-700">
            <span>Histórico</span>
            <span className="rounded-full bg-neutral-100 px-2 py-0.5 text-xs">{movements.length}</span>
          </div>
          <div className="max-h-[200px] space-y-1 overflow-y-auto">
            {movements.map((movement) => {
              const isLegacy =
                movement.isLegacy ??
                (typeof movement.previousStock === 'number' &&
                  typeof movement.previousUnitsInStock !== 'number')

              // Resumo de delta. Pos-V14: mostra (stock,use). Pre-V14: mostra previousStock.
              const deltaText = (() => {
                if (!isLegacy) {
                  const ps = movement.previousUnitsInStock ?? null
                  const pu = movement.previousUnitsInUse ?? null
                  if (ps != null && pu != null) {
                    return `📦${ps} 🔓${pu}`
                  }
                  return null
                }
                const ps = movement.previousStock ?? null
                if (ps == null) return null
                let next: number | null = null
                if (movement.type === 'ENTRADA') next = ps + movement.quantity
                else if (movement.type === 'SAIDA') next = ps - movement.quantity
                else if (movement.type === 'AJUSTE') next = movement.quantity
                return next != null ? `${ps} → ${next}` : null
              })()

              const sign =
                movement.type === 'ENTRADA' || movement.type === 'FECHAMENTO'
                  ? '+'
                  : movement.type === 'SAIDA' ||
                      movement.type === 'CONSUMO' ||
                      movement.type === 'ABERTURA'
                    ? '-'
                    : '='

              const colorClass =
                movement.type === 'ENTRADA' || movement.type === 'FECHAMENTO'
                  ? 'text-green-700'
                  : movement.type === 'SAIDA' ||
                      movement.type === 'CONSUMO' ||
                      movement.type === 'ABERTURA'
                    ? 'text-red-700'
                    : 'text-blue-700'

              const Icon =
                movement.type === 'ENTRADA' || movement.type === 'FECHAMENTO'
                  ? ArrowDownLeft
                  : movement.type === 'SAIDA' ||
                      movement.type === 'CONSUMO' ||
                      movement.type === 'ABERTURA'
                    ? ArrowUpRight
                    : Pencil

              return (
                <div
                  key={movement.id}
                  className="flex items-center justify-between rounded-lg border border-neutral-100 px-3 py-2 text-sm"
                >
                  <div className="flex items-center gap-2 flex-wrap">
                    <Icon className={cn('h-3.5 w-3.5', colorClass)} />
                    <span className={cn('font-semibold', colorClass)}>
                      {sign}
                      {movement.quantity}
                    </span>
                    <span className="rounded-full bg-neutral-100 px-2 py-0.5 text-[10px] uppercase tracking-wide text-neutral-600">
                      {movement.type}
                    </span>
                    {deltaText ? (
                      <span className="text-xs text-neutral-500 font-mono">{deltaText}</span>
                    ) : null}
                    {movement.responsible ? (
                      <span className="text-neutral-400">por {movement.responsible}</span>
                    ) : null}
                    {movement.reason ? (
                      <span className="rounded-full bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-800">
                        {MOVEMENT_REASONS.find((reason) => reason.value === movement.reason)?.label ??
                          movement.reason}
                      </span>
                    ) : null}
                  </div>
                  <span className="text-xs text-neutral-400">
                    {new Date(movement.createdAt).toLocaleDateString('pt-BR')}
                  </span>
                </div>
              )
            })}
          </div>
        </div>
      ) : null}
    </Modal>
  )
}

function FormSection({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div>
      <div className="mb-2 text-sm font-semibold uppercase tracking-wide text-green-800">{title}</div>
      <div className="border-l-2 border-green-200 pl-4">{children}</div>
    </div>
  )
}
