import axios from 'axios'
import { AlertTriangle, ShieldCheck } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useLabSettings } from '../../hooks/useLabSettings'
import { useSignReportV2 } from '../../hooks/useReportsV2'
import type { ReportExecutionResponse } from '../../types/reportsV2'
import { Button, Input, Modal, useToast } from '../ui'

interface SignReportModalProps {
  execution: ReportExecutionResponse
  onClose: () => void
  onSigned: (updated: ReportExecutionResponse) => void
}

/**
 * Modal de assinatura de relatorio V2. Pre-preenche {@code signerName} e
 * {@code signerRegistration} com os valores de {@code LabSettings} quando
 * disponiveis (o backend ja faz fallback, mas mostrar os dados ajuda o
 * revisor a confirmar quem esta assinando).
 *
 * <p>Se o backend responder 422 com mensagem mencionando registro profissional
 * ausente (InvalidSigner), exibimos ponteiro direto para configuracao.
 */
export function SignReportModal({ execution, onClose, onSigned }: SignReportModalProps) {
  const { toast } = useToast()
  const settingsQuery = useLabSettings()
  const signMutation = useSignReportV2()

  const [signerName, setSignerName] = useState('')
  const [signerRegistration, setSignerRegistration] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isInvalidSigner, setIsInvalidSigner] = useState(false)

  useEffect(() => {
    const data = settingsQuery.data
    if (!data) return
    setSignerName((current) => current || data.responsibleName || '')
    setSignerRegistration((current) => current || data.responsibleRegistration || '')
  }, [settingsQuery.data])

  const handleSign = async () => {
    setError(null)
    setIsInvalidSigner(false)
    try {
      const updated = await signMutation.mutateAsync({
        id: execution.id,
        signerName: signerName.trim() || undefined,
        signerRegistration: signerRegistration.trim() || undefined,
      })
      toast.success(`Relatorio ${updated.reportNumber ?? ''} assinado com sucesso.`)
      onSigned(updated)
    } catch (err) {
      const status = axios.isAxiosError(err) ? err.response?.status : undefined
      const message = extractErrorMessage(err)
      setError(message)
      if (status === 422 && /registro profissional/i.test(message)) {
        setIsInvalidSigner(true)
      }
    }
  }

  return (
    <Modal
      isOpen
      onClose={onClose}
      title={`Assinar ${execution.reportNumber ?? 'relatorio'}`}
      footer={
        <div className="flex items-center justify-end gap-3">
          <Button variant="secondary" onClick={onClose} disabled={signMutation.isPending}>
            Cancelar
          </Button>
          <Button
            onClick={() => void handleSign()}
            loading={signMutation.isPending}
            icon={<ShieldCheck className="h-4 w-4" />}
          >
            Assinar
          </Button>
        </div>
      }
    >
      <div className="space-y-4">
        <div className="rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
          Apos assinar, este relatorio <strong>nao pode ser modificado</strong>. Uma copia original
          permanece acessivel via auditoria.
        </div>

        <Input
          label="Nome do responsavel"
          value={signerName}
          onChange={(event) => setSignerName(event.target.value)}
          placeholder="Nome completo"
        />
        <Input
          label="Registro profissional (CRBM/CRM)"
          value={signerRegistration}
          onChange={(event) => setSignerRegistration(event.target.value)}
          placeholder="Ex: CRBM-X 00000"
        />

        {isInvalidSigner ? (
          <div className="flex items-start gap-3 rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-800">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
            <div>
              <p className="font-semibold">Registro profissional obrigatorio</p>
              <p className="mt-1">
                O laudo nao tem validade juridica sem registro do responsavel tecnico. Configure em{' '}
                <code>Configuracao &gt; Laboratorio</code> ou informe o valor acima.
              </p>
            </div>
          </div>
        ) : error ? (
          <div className="rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-800">
            {error}
          </div>
        ) : null}
      </div>
    </Modal>
  )
}

function extractErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as
      | { message?: string; error?: string; detail?: string; title?: string }
      | undefined
    return data?.detail ?? data?.message ?? data?.title ?? data?.error ?? error.message
  }
  if (error instanceof Error) return error.message
  return 'Erro desconhecido'
}
