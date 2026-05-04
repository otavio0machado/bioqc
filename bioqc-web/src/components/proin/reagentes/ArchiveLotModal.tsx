import { Archive } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import type { ReagentLot, ResponsibleSummary } from '../../../types'
import { Button, Combobox, Input, Modal, type ComboboxOption } from '../../ui'
import { todayLocal } from '../../../utils/date'

interface ArchiveLotModalProps {
  isOpen: boolean
  isSaving: boolean
  lot: ReagentLot | null
  responsibles: ResponsibleSummary[]
  onClose: () => void
  onConfirm: (payload: { archivedAt: string; archivedBy: string }) => void
}

/**
 * Modal de arquivamento de lote (refator v3).
 *
 * Fluxo:
 * - Pede {@code archivedAt} (date, max=hoje, default=hoje).
 * - Pede {@code archivedBy} via combobox de FUNCIONARIO/ADMIN ativos.
 *   Selecao envia o {@code username} (decisao audit 1.1 — chave estavel).
 * - Backend valida {@code archivedBy} contra {@code users}.
 *
 * NAO oferece "+ Criar novo" — combobox eh fechado: archive responsavel tem que
 * existir no quadro.
 */
export function ArchiveLotModal({
  isOpen,
  isSaving,
  lot,
  responsibles,
  onClose,
  onConfirm,
}: ArchiveLotModalProps) {
  const today = todayLocal()
  const [archivedAt, setArchivedAt] = useState<string>(today)
  const [archivedBy, setArchivedBy] = useState<string>('')

  useEffect(() => {
    if (isOpen) {
      setArchivedAt(today)
      setArchivedBy('')
    }
  }, [isOpen, today])

  const responsibleOptions = useMemo<ComboboxOption[]>(
    () =>
      responsibles
        .map((user) => ({
          value: user.username,
          label: user.name?.trim() ? user.name : user.username,
          description: `${user.role}${user.username ? ` · ${user.username}` : ''}`,
        }))
        .sort((a, b) => a.label.localeCompare(b.label)),
    [responsibles],
  )

  const isValid = Boolean(
    archivedAt &&
      archivedAt <= today &&
      archivedBy.trim() &&
      responsibleOptions.some((option) => option.value === archivedBy.trim()),
  )

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={`Arquivar lote${lot ? ` · ${lot.lotNumber}` : ''}`}
      size="md"
      footer={
        <div className="flex justify-end gap-3">
          <Button variant="ghost" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            onClick={() =>
              onConfirm({ archivedAt, archivedBy: archivedBy.trim() })
            }
            disabled={!isValid}
            loading={isSaving}
            icon={<Archive className="h-4 w-4" />}
          >
            Arquivar lote
          </Button>
        </div>
      }
    >
      <p className="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
        O lote ficará na aba <strong>Inativos</strong> e não aceitará novas movimentações até ser
        reativado. O histórico de movimentações é preservado.
      </p>

      <div className="grid gap-3 sm:grid-cols-2">
        <Input
          label="Data de arquivamento *"
          type="date"
          max={today}
          value={archivedAt}
          onChange={(event) => setArchivedAt(event.target.value)}
        />
        <Combobox
          id="archive-responsible-input"
          label="Responsável *"
          placeholder="Selecione o responsável..."
          value={archivedBy}
          onChange={(value) => setArchivedBy(value)}
          options={responsibleOptions}
          allowCustom={false}
          emptyText="Nenhum responsável disponível"
        />
      </div>

      {lot ? (
        <div className="mt-4 rounded-lg border border-neutral-200 bg-neutral-50 px-3 py-2 text-xs text-neutral-600">
          <strong className="text-neutral-700">{lot.label}</strong> · Lote {lot.lotNumber} ·{' '}
          {lot.manufacturer}
          <br />
          Em estoque: {lot.unitsInStock ?? 0} · Em uso: {lot.unitsInUse ?? 0}
        </div>
      ) : null}
    </Modal>
  )
}
