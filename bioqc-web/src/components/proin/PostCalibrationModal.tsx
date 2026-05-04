import axios from 'axios'
import { useState } from 'react'
import { useCreatePostCalibration } from '../../hooks/useQcRecords'
import type { PostCalibrationRecord, QcRecord } from '../../types'
import { Button, Input, Modal, TextArea, useToast } from '../ui'

interface PostCalibrationModalProps {
  record: QcRecord | null
  isOpen: boolean
  onClose: () => void
  onSaved?: (postCalibration: PostCalibrationRecord) => void
}

export function PostCalibrationModal({ record, isOpen, onClose, onSaved }: PostCalibrationModalProps) {
  const { toast } = useToast()
  const mutation = useCreatePostCalibration(record?.id ?? '')
  const [form, setForm] = useState(() => buildInitialForm(record))

  const handleSubmit = async () => {
    if (!record || !form.postCalibrationValue) {
      toast.warning('Informe o valor pós-calibração para salvar.')
      return
    }
    if (!record.needsCalibration) {
      toast.warning('A pós-calibração só pode ser registrada quando existe pendência corretiva ativa.')
      return
    }

    try {
      const response = await mutation.mutateAsync({
        date: form.date,
        postCalibrationValue: Number(form.postCalibrationValue),
        analyst: form.analyst,
        notes: form.notes,
      })
      onSaved?.(response)
      toast.success('Pós-calibração registrada com sucesso.')
      onClose()
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Não foi possível salvar a pós-calibração.'))
    }
  }

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Registrar Pós-Calibração"
      footer={
        <div className="flex justify-end gap-3">
          <Button variant="ghost" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={handleSubmit} loading={mutation.isPending}>
            Salvar
          </Button>
        </div>
      }
    >
      <div className="mb-4 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
        O pós-calibração registra a ação corretiva vinculada a este evento. Ele não altera o status nem reescreve o resultado original do CQ.
      </div>
      <div className="grid gap-4 md:grid-cols-2">
        <Input label="Exame" value={record?.examName ?? ''} readOnly />
        <Input label="Valor original" value={record ? record.value.toFixed(2) : ''} readOnly />
        <Input
          label="Data"
          type="date"
          value={form.date}
          onChange={(event) => setForm((current) => ({ ...current, date: event.target.value }))}
        />
        <Input
          label="Valor pós-calibração"
          type="number"
          step="0.01"
          value={form.postCalibrationValue}
          onChange={(event) => setForm((current) => ({ ...current, postCalibrationValue: event.target.value }))}
        />
        <Input
          label="Analista"
          value={form.analyst}
          onChange={(event) => setForm((current) => ({ ...current, analyst: event.target.value }))}
        />
      </div>
      <div className="mt-4">
        <TextArea
          label="Observações"
          value={form.notes}
          onChange={(event) => setForm((current) => ({ ...current, notes: event.target.value }))}
        />
      </div>
    </Modal>
  )
}

function getApiErrorMessage(error: unknown, fallbackMessage: string) {
  if (axios.isAxiosError(error)) {
    const apiMessage = error.response?.data?.message
    if (typeof apiMessage === 'string' && apiMessage.trim()) {
      return apiMessage
    }
  }
  return fallbackMessage
}

function buildInitialForm(record: QcRecord | null) {
  return {
    date: record?.date ?? new Date().toISOString().slice(0, 10),
    postCalibrationValue: '',
    analyst: '',
    notes: '',
  }
}
