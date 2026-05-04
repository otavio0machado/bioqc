import { AlertTriangle, CheckCircle2, FileSignature, ShieldCheck } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { useVerifyReport } from '../hooks/useReportsV2'
import { LoadingSpinner } from '../components/ui'

/**
 * Pagina publica de verificacao de laudo (Fluxo D).
 *
 * <p>Rota: {@code /r/verify/:hash}. Nao depende de autenticacao; o endpoint
 * {@code /api/reports/v2/verify/{hash}} tambem e publico (permitAll no
 * SecurityConfig).
 *
 * <p>UX:
 * <ul>
 *   <li>valid=true, signed=true -&gt; banner verde "Documento valido e assinado"</li>
 *   <li>valid=true, signed=false -&gt; banner azul "Documento valido (nao assinado)"</li>
 *   <li>valid=false -&gt; banner vermelho "Hash desconhecido"</li>
 *   <li>Erro de rede -&gt; banner neutro com ponteiro para tentar de novo</li>
 * </ul>
 */
export function VerifyReportPage() {
  const { hash } = useParams<{ hash: string }>()
  const query = useVerifyReport(hash)

  return (
    <div className="min-h-screen bg-neutral-50">
      <header className="border-b border-neutral-200 bg-white py-6">
        <div className="mx-auto flex max-w-3xl items-center gap-3 px-4 sm:px-6">
          <div className="rounded-xl bg-green-100 p-2 text-green-800">
            <FileSignature className="h-6 w-6" />
          </div>
          <div>
            <h1 className="text-xl font-semibold text-neutral-900">BioQC</h1>
            <p className="text-xs text-neutral-500">Verificacao publica de laudo</p>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-3xl px-4 py-8 sm:px-6">
        {query.isLoading ? (
          <div className="flex min-h-56 items-center justify-center">
            <LoadingSpinner size="lg" className="text-green-800" />
          </div>
        ) : query.isError ? (
          <div className="rounded-2xl border border-neutral-300 bg-white p-6 text-center text-sm text-neutral-600">
            <p>Nao foi possivel consultar o servico de verificacao agora.</p>
            <p className="mt-2 text-xs text-neutral-500">
              Tente novamente em instantes. Se o problema persistir, contate o laboratorio.
            </p>
          </div>
        ) : query.data ? (
          <VerifyResult data={query.data} hash={hash ?? ''} />
        ) : null}

        <footer className="mt-10 text-center">
          <Link to="/login" className="text-sm text-neutral-500 underline hover:text-neutral-800">
            Voltar para login
          </Link>
        </footer>
      </main>
    </div>
  )
}

interface VerifyResultProps {
  data: {
    reportNumber: string | null
    reportCode: string | null
    periodLabel: string | null
    generatedAt: string | null
    generatedByName: string | null
    sha256: string | null
    signatureHash: string | null
    signedAt: string | null
    signedByName: string | null
    signed: boolean
    valid: boolean
  }
  hash: string
}

function VerifyResult({ data, hash }: VerifyResultProps) {
  if (!data.valid) {
    return (
      <div className="space-y-4">
        <div
          role="alert"
          className="flex items-start gap-3 rounded-2xl border border-red-300 bg-red-50 p-5 text-red-800"
        >
          <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0" />
          <div>
            <p className="text-base font-semibold">Hash desconhecido</p>
            <p className="mt-1 text-sm">
              Este documento <strong>nao foi emitido pelo BioQC</strong> ou o hash
              informado nao corresponde a nenhum laudo registrado.
            </p>
            <p className="mt-2 break-all font-mono text-xs text-red-700">{hash}</p>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      {data.signed ? (
        <div
          role="status"
          className="flex items-start gap-3 rounded-2xl border border-emerald-300 bg-emerald-50 p-5 text-emerald-900"
        >
          <ShieldCheck className="mt-0.5 h-5 w-5 shrink-0" />
          <div>
            <p className="text-base font-semibold">Documento valido e assinado</p>
            <p className="mt-1 text-sm">
              Este laudo foi gerado e assinado digitalmente pelo BioQC.
            </p>
          </div>
        </div>
      ) : (
        <div
          role="status"
          className="flex items-start gap-3 rounded-2xl border border-blue-300 bg-blue-50 p-5 text-blue-900"
        >
          <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0" />
          <div>
            <p className="text-base font-semibold">Documento valido (nao assinado)</p>
            <p className="mt-1 text-sm">
              Laudo gerado pelo BioQC, ainda sem assinatura do responsavel tecnico.
            </p>
          </div>
        </div>
      )}

      <div className="rounded-2xl border border-neutral-200 bg-white p-6">
        <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Item label="Numero do laudo" value={data.reportNumber} mono />
          <Item label="Tipo" value={data.reportCode} />
          <Item label="Periodo" value={data.periodLabel} />
          <Item
            label="Gerado em"
            value={data.generatedAt ? new Date(data.generatedAt).toLocaleString('pt-BR') : null}
          />
          <Item label="Gerado por" value={data.generatedByName} />
          {data.signed ? (
            <>
              <Item label="Assinado por" value={data.signedByName} />
              <Item
                label="Assinado em"
                value={data.signedAt ? new Date(data.signedAt).toLocaleString('pt-BR') : null}
              />
            </>
          ) : null}
        </dl>

        <div className="mt-6 space-y-2 border-t border-neutral-200 pt-4">
          {data.sha256 ? (
            <div>
              <p className="text-xs uppercase tracking-wider text-neutral-500">SHA-256 (original)</p>
              <p className="mt-1 break-all font-mono text-xs text-neutral-700">{data.sha256}</p>
            </div>
          ) : null}
          {data.signatureHash ? (
            <div>
              <p className="text-xs uppercase tracking-wider text-neutral-500">SHA-256 (assinado)</p>
              <p className="mt-1 break-all font-mono text-xs text-neutral-700">
                {data.signatureHash}
              </p>
            </div>
          ) : null}
        </div>
      </div>
    </div>
  )
}

function Item({ label, value, mono }: { label: string; value: string | null; mono?: boolean }) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wider text-neutral-500">{label}</dt>
      <dd className={`mt-1 text-sm text-neutral-800 ${mono ? 'font-mono' : ''}`}>
        {value ?? '-'}
      </dd>
    </div>
  )
}
