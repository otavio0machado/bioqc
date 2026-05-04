import { Unlock } from 'lucide-react'
import { useEffect, useState } from 'react'
import type { ReagentLot } from '../../../types'
import { Button, Input, Modal } from '../../ui'
import { todayLocal } from '../../../utils/date'

interface OpenUnitModalProps {
  isOpen: boolean
  isSaving: boolean
  lot: ReagentLot | null
  onClose: () => void
  onConfirm: (eventDate: string) => void
}

/**
 * Modal de abertura de unidade (refator v3.1).
 *
 * Fluxo:
 * - Confirma a data declarada de abertura ({@code eventDate}, LocalDate
 *   "YYYY-MM-DD"). Default = hoje. Editavel para registrar com atraso
 *   ("abriu ontem e esqueceu").
 * - Validacao client: {@code eventDate <= today} (backend tambem valida).
 * - O evento dispara um movimento {@code ABERTURA q=1} cujo
 *   {@code lot.openedDate} sera o {@code eventDate} escolhido.
 *
 * Padrao visual espelha {@link ArchiveLotModal}: mesmo {@code Modal} +
 * {@code Input}.
 */
export function OpenUnitModal({
  isOpen,
  isSaving,
  lot,
  onClose,
  onConfirm,
}: OpenUnitModalProps) {
  const today = todayLocal()
  const [eventDate, setEventDate] = useState<string>(today)

  useEffect(() => {
    if (isOpen) {
      setEventDate(today)
    }
  }, [isOpen, today])

  const isFuture = Boolean(eventDate) && eventDate > today
  const isValid = Boolean(eventDate) && !isFuture

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={`Abrir unidade${lot ? ` · Lote ${lot.lotNumber}` : ''}`}
      size="md"
      footer={
        <div className="flex justify-end gap-3">
          <Button variant="ghost" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            onClick={() => onConfirm(eventDate)}
            disabled={!isValid}
            loading={isSaving}
            icon={<Unlock className="h-4 w-4" />}
          >
            Confirmar abertura
          </Button>
        </div>
      }
    >
      <p className="mb-4 text-sm text-neutral-600">
        Confirme a data em que a unidade foi de fato aberta. Padrão é hoje, mas você pode
        editar (ex: abriu ontem e esqueceu de registrar).
      </p>

      <div className="grid gap-3 sm:grid-cols-1">
        <Input
          label="Data de abertura *"
          type="date"
          max={today}
          value={eventDate}
          onChange={(event) => setEventDate(event.target.value)}
        />
      </div>

      {isFuture ? (
        <p className="mt-3 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm font-medium text-red-800">
          A data de abertura não pode ser futura.
        </p>
      ) : null}

      {lot ? (
        <div className="mt-4 rounded-lg border border-neutral-200 bg-neutral-50 px-3 py-2 text-xs text-neutral-600">
          <strong className="text-neutral-700">{lot.label}</strong> · Lote {lot.lotNumber}
          {lot.manufacturer ? ` · ${lot.manufacturer}` : ''}
          <br />
          Em estoque atual: <strong>{lot.unitsInStock ?? 0}</strong> · Em uso:{' '}
          <strong>{lot.unitsInUse ?? 0}</strong>
        </div>
      ) : null}
    </Modal>
  )
}
