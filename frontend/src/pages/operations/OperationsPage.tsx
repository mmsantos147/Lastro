import { useSearchParams } from 'react-router'
import { CatalogTab } from './CatalogTab'
import { CorporateActionsTab } from './CorporateActionsTab'
import { DistributionsTab } from './DistributionsTab'
import { ImportsTab } from './ImportsTab'
import { QuotesTab } from './QuotesTab'
import { TradesTab } from './TradesTab'
import { TransfersTab } from './TransfersTab'

const tabs = [
  { id: 'operacoes', label: 'Compras e vendas', element: <TradesTab /> },
  { id: 'transferencias', label: 'Transferências', element: <TransfersTab /> },
  { id: 'proventos', label: 'Proventos', element: <DistributionsTab /> },
  { id: 'eventos', label: 'Eventos corporativos', element: <CorporateActionsTab /> },
  { id: 'importar', label: 'Importar arquivos', element: <ImportsTab /> },
  { id: 'cadastros', label: 'Ativos e instituições', element: <CatalogTab /> },
  { id: 'cotacoes', label: 'Cotações', element: <QuotesTab /> },
] as const

/**
 * Manual entry and file import of everything the positions are derived from. The active tab lives in the URL (?aba=).
 */
export function OperationsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const active = tabs.find((tab) => tab.id === searchParams.get('aba')) ?? tabs[0]

  return (
    <section className="stack">
      <h1>Operações</h1>
      <div role="tablist" className="tabs" aria-label="Tipo de registro">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            type="button"
            role="tab"
            aria-selected={tab.id === active.id}
            className="tab"
            onClick={() => setSearchParams({ aba: tab.id })}
          >
            {tab.label}
          </button>
        ))}
      </div>
      <div role="tabpanel">{active.element}</div>
    </section>
  )
}
