import { ReagentesTab } from '../components/proin/ReagentesTab'

export function ReagentesPage() {
  return (
    <div className="mx-auto max-w-7xl space-y-6 px-4 py-8 sm:px-6 lg:px-8">
      <header>
        <h1 className="text-3xl font-bold text-neutral-900">Reagentes</h1>
        <p className="text-base text-neutral-500">Controle de estoque, lotes e validade.</p>
      </header>
      <ReagentesTab />
    </div>
  )
}
