import { useEffect, useState } from 'react'
import { Save, Trash2, Plus } from 'lucide-react'
import { Button, Card, Input, Select, useToast } from '../components/ui'
import {
  useLabSettings,
  useLabReportEmails,
  useAddLabReportEmail,
  useRemoveLabReportEmail,
  useToggleLabReportEmail,
  useUpdateLabSettings,
} from '../hooks/useLabSettings'
import type { LabSettings } from '../services/labSettingsService'

const emptySettings: LabSettings = {
  labName: '',
  responsibleName: '',
  responsibleRegistration: '',
  address: '',
  phone: '',
  email: '',
  cnpj: '',
  cnes: '',
  registrationBody: '',
  responsibleCpf: '',
  technicalDirectorName: '',
  technicalDirectorCpf: '',
  technicalDirectorReg: '',
  website: '',
  sanitaryLicense: '',
}

const CNPJ_REGEX = /^\d{2}\.\d{3}\.\d{3}\/\d{4}-\d{2}$/
const REGISTRATION_BODIES = ['CRBM', 'CRM', 'CRF', 'CRN', 'Outro']

export function ConfiguracaoPage() {
  const { toast } = useToast()
  const { data: settings } = useLabSettings()
  const { data: emails = [] } = useLabReportEmails()
  const updateSettings = useUpdateLabSettings()
  const addEmail = useAddLabReportEmail()
  const toggleEmail = useToggleLabReportEmail()
  const removeEmail = useRemoveLabReportEmail()

  const [form, setForm] = useState<LabSettings>(emptySettings)
  const [newEmail, setNewEmail] = useState('')
  const [newEmailName, setNewEmailName] = useState('')

  useEffect(() => {
    if (settings) setForm(settings)
  }, [settings])

  const handleSave = async () => {
    // Validação cliente-side para campos institucionais opcionais
    if (form.cnpj && form.cnpj.trim() && !CNPJ_REGEX.test(form.cnpj.trim())) {
      toast.warning('CNPJ deve seguir o formato XX.XXX.XXX/XXXX-XX.')
      return
    }
    if (form.cnes && form.cnes.trim() && !/^\d{7}$/.test(form.cnes.trim())) {
      toast.warning('CNES deve ter 7 dígitos.')
      return
    }
    try {
      await updateSettings.mutateAsync(form)
      toast.success('Configurações salvas.')
    } catch {
      toast.error('Não foi possível salvar.')
    }
  }

  const handleAddEmail = async () => {
    if (!newEmail.trim()) {
      toast.warning('Informe o e-mail.')
      return
    }
    try {
      await addEmail.mutateAsync({ email: newEmail.trim(), name: newEmailName.trim() })
      setNewEmail('')
      setNewEmailName('')
      toast.success('E-mail adicionado.')
    } catch {
      toast.error('Não foi possível adicionar o e-mail.')
    }
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6 px-4 py-8 sm:px-6 lg:px-8">
      <header>
        <h1 className="text-3xl font-bold text-neutral-900">Configuração</h1>
        <p className="text-base text-neutral-500">Dados do laboratório usados na capa dos relatórios e envio automático.</p>
      </header>

      <Card className="space-y-4">
        <div>
          <h2 className="text-lg font-semibold text-neutral-900">Dados do Laboratório</h2>
          <p className="text-sm text-neutral-500">Aparecem na capa de todos os relatórios gerados.</p>
        </div>
        <div className="grid gap-4 md:grid-cols-2">
          <Input label="Nome do Laboratório" value={form.labName}
            onChange={(e) => setForm((c) => ({ ...c, labName: e.target.value }))} />
          <Input label="Endereço" value={form.address}
            onChange={(e) => setForm((c) => ({ ...c, address: e.target.value }))} />
          <Input label="Responsável Técnico" value={form.responsibleName}
            onChange={(e) => setForm((c) => ({ ...c, responsibleName: e.target.value }))} />
          <Input label="Registro (CRF, CRBM…)" value={form.responsibleRegistration}
            onChange={(e) => setForm((c) => ({ ...c, responsibleRegistration: e.target.value }))} />
          <Input label="Telefone" value={form.phone}
            onChange={(e) => setForm((c) => ({ ...c, phone: e.target.value }))} />
          <Input label="E-mail de contato" value={form.email}
            onChange={(e) => setForm((c) => ({ ...c, email: e.target.value }))} />
        </div>

        <div className="border-t border-neutral-100 pt-4">
          <h3 className="text-base font-semibold text-neutral-900">Dados Institucionais (regulatório)</h3>
          <p className="text-sm text-neutral-500">
            Usados na capa de relatórios V2 e no pacote regulatório para a Vigilância Sanitária. Campos opcionais,
            mas recomendados para laudos oficiais.
          </p>
        </div>
        <div className="grid gap-4 md:grid-cols-2">
          <Input
            label="CNPJ"
            placeholder="XX.XXX.XXX/XXXX-XX"
            value={form.cnpj ?? ''}
            onChange={(e) => setForm((c) => ({ ...c, cnpj: e.target.value }))}
          />
          <Input
            label="CNES"
            placeholder="7 dígitos"
            maxLength={7}
            value={form.cnes ?? ''}
            onChange={(e) => setForm((c) => ({ ...c, cnes: e.target.value.replace(/\D/g, '') }))}
          />
          <Input
            label="Licença Sanitária"
            placeholder="Número da licença"
            value={form.sanitaryLicense ?? ''}
            onChange={(e) => setForm((c) => ({ ...c, sanitaryLicense: e.target.value }))}
          />
          <Input
            label="Website"
            placeholder="https://..."
            value={form.website ?? ''}
            onChange={(e) => setForm((c) => ({ ...c, website: e.target.value }))}
          />
          <div className="space-y-1">
            <Select
              label="Órgão de registro do responsável"
              value={form.registrationBody ?? ''}
              onChange={(e) => setForm((c) => ({ ...c, registrationBody: e.target.value }))}
            >
              <option value="">Selecione...</option>
              {REGISTRATION_BODIES.map((body) => (
                <option key={body} value={body}>{body}</option>
              ))}
            </Select>
          </div>
          <Input
            label="CPF do Responsável"
            placeholder="000.000.000-00"
            value={form.responsibleCpf ?? ''}
            onChange={(e) => setForm((c) => ({ ...c, responsibleCpf: e.target.value }))}
          />
        </div>

        <div className="pt-2">
          <h4 className="text-sm font-semibold text-neutral-800">Diretor Técnico</h4>
          <p className="text-xs text-neutral-500">Aparece no pacote regulatório ANVISA.</p>
        </div>
        <div className="grid gap-4 md:grid-cols-3">
          <Input
            label="Nome"
            value={form.technicalDirectorName ?? ''}
            onChange={(e) => setForm((c) => ({ ...c, technicalDirectorName: e.target.value }))}
          />
          <Input
            label="CPF"
            placeholder="000.000.000-00"
            value={form.technicalDirectorCpf ?? ''}
            onChange={(e) => setForm((c) => ({ ...c, technicalDirectorCpf: e.target.value }))}
          />
          <Input
            label="Registro profissional"
            value={form.technicalDirectorReg ?? ''}
            onChange={(e) => setForm((c) => ({ ...c, technicalDirectorReg: e.target.value }))}
          />
        </div>

        <div className="flex justify-end">
          <Button onClick={handleSave} loading={updateSettings.isPending} icon={<Save className="h-4 w-4" />}>
            Salvar
          </Button>
        </div>
      </Card>

      <Card className="space-y-4">
        <div>
          <h2 className="text-lg font-semibold text-neutral-900">Destinatários de Relatórios</h2>
          <p className="text-sm text-neutral-500">E-mails que receberão relatórios enviados pelo sistema.</p>
        </div>

        <div className="grid gap-3 md:grid-cols-[2fr_2fr_auto]">
          <Input label="E-mail" placeholder="responsavel@laboratorio.com" value={newEmail}
            onChange={(e) => setNewEmail(e.target.value)} />
          <Input label="Nome (opcional)" placeholder="Dr. Fulano" value={newEmailName}
            onChange={(e) => setNewEmailName(e.target.value)} />
          <div className="flex items-end">
            <Button onClick={handleAddEmail} loading={addEmail.isPending} icon={<Plus className="h-4 w-4" />}>
              Adicionar
            </Button>
          </div>
        </div>

        {emails.length === 0 ? (
          <div className="rounded-xl border border-neutral-200 bg-neutral-50 px-4 py-6 text-center text-sm text-neutral-500">
            Nenhum e-mail cadastrado.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-neutral-100 text-xs uppercase tracking-wider text-neutral-500">
                  <th className="px-3 py-2.5">E-mail</th>
                  <th className="px-3 py-2.5">Nome</th>
                  <th className="px-3 py-2.5">Ativo</th>
                  <th className="px-3 py-2.5 text-right">Ação</th>
                </tr>
              </thead>
              <tbody>
                {emails.map((entry) => (
                  <tr key={entry.id} className="border-b border-neutral-50 hover:bg-neutral-50/50">
                    <td className="px-3 py-2.5 font-medium text-neutral-900">{entry.email}</td>
                    <td className="px-3 py-2.5 text-neutral-700">{entry.name || '—'}</td>
                    <td className="px-3 py-2.5">
                      <label className="inline-flex items-center gap-2">
                        <input
                          type="checkbox"
                          checked={entry.isActive}
                          onChange={(e) =>
                            void toggleEmail.mutateAsync({ id: entry.id, active: e.target.checked })
                          }
                        />
                        <span className="text-sm">{entry.isActive ? 'Sim' : 'Não'}</span>
                      </label>
                    </td>
                    <td className="px-3 py-2.5 text-right">
                      <button
                        type="button"
                        className="text-red-400 transition hover:text-red-600"
                        onClick={() => void removeEmail.mutateAsync(entry.id)}
                        title="Remover"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  )
}
