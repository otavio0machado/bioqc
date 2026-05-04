import { ManutencaoTab } from '../components/proin/ManutencaoTab'

export function ManutencaoPage() {
  return (
    <div className="mx-auto max-w-7xl space-y-6 px-4 py-8 sm:px-6 lg:px-8">
      <header>
        <h1 className="text-3xl font-bold text-neutral-900">Manutenção</h1>
        <p className="text-base text-neutral-500">Registro e histórico de manutenções de equipamentos.</p>
      </header>
      <ManutencaoTab />
    </div>
  )
}
