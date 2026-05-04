import { AlertTriangle, Trash2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import type { ReagentLot } from '../../../types'
import { Button, Input, Modal } from '../../ui'

interface DeleteLotModalProps {
  isOpen: boolean
  isSaving: boolean
  lot: ReagentLot | null
  onClose: () => void
  onConfirm: (payload: { confirmLotNumber: string }) => void
}

/**
 * Modal de hard delete (refator v3, ADMIN-only).
 *
 * Defesa contra delete acidental: usuario tem que digitar o numero do lote
 * exatamente igual para habilitar o botao de "Apagar definitivamente". Backend
 * tambem valida {@code confirmLotNumber} via {@link DeleteReagentLotRequest}.
 *
 * Banner extra-vermelho quando {@code usedInQcRecently=true} — backend bloqueia
 * com 409, mas avisa antes para nao desperdicar ciclo.
 */
export function DeleteLotModal({
  isOpen,
  isSaving,
  lot,
  onClose,
  onConfirm,
}: DeleteLotModalProps) {
  const [confirmation, setConfirmation] = useState('')

  useEffect(() => {
    if (isOpen) setConfirmation('')
  }, [isOpen])

  const expected = lot?.lotNumber?.trim() ?? ''
  const isMatch = confirmation.trim() === expected && expected.length > 0

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={`Apagar lote${lot ? ` · ${lot.lotNumber}` : ''}`}
      size="md"
      footer={
        <div className="flex justify-end gap-3">
          <Button variant="ghost" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            variant="danger"
            onClick={() => onConfirm({ confirmLotNumber: confirmation.trim() })}
            disabled={!isMatch}
            loading={isSaving}
            icon={<Trash2 className="h-4 w-4" />}
          >
            Apagar definitivamente
          </Button>
        </div>
      }
    >
      <div className="space-y-3">
        <div className="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
          <span>
            Esta ação é <strong>irreversível</strong>. O lote e todo o histórico de movimentações
            serão removidos. Use apenas se o lote foi cadastrado por engano.
          </span>
        </div>

        {lot?.usedInQcRecently ? (
          <div className="flex items-start gap-2 rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-900">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
            <span>
              Este lote apareceu em CQ recente. O servidor recusará a operação para preservar
              rastreabilidade. Considere arquivar.
            </span>
          </div>
        ) : null}

        <div className="space-y-1">
          <Input
            label={`Para confirmar, digite o número do lote: ${expected}`}
            value={confirmation}
            onChange={(event) => setConfirmation(event.target.value)}
            placeholder={expected}
            autoFocus
          />
          {confirmation.length > 0 && !isMatch ? (
            <p className="text-xs text-red-600">O número do lote não confere.</p>
          ) : null}
        </div>
      </div>
    </Modal>
  )
}
