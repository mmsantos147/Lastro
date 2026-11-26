import { useState } from 'react'
import { useDeleteStrategy, useSetStrategyActive, useStrategies, type Strategy } from '../../api/strategy'
import { QueryState, RequestError } from '../../components/form'
import { assetClassLabels } from '../../lib/labels'
import { useCatalogLookup } from '../operations/useCatalogLookup'
import { StrategyForm } from './StrategyForm'

const percent = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 2 })

export function StrategyPage() {
  const strategies = useStrategies()
  const setActive = useSetStrategyActive()
  const deleteStrategy = useDeleteStrategy()
  const catalog = useCatalogLookup()
  const [editing, setEditing] = useState<Strategy | 'new'>()

  return (
    <section className="stack">
      <div className="page-header">
        <h1>Estratégia</h1>
        {!editing && (
          <button type="button" className="button" onClick={() => setEditing('new')}>
            Nova estratégia
          </button>
        )}
      </div>
      <p className="muted">
        A alocação alvo orienta a sugestão de aporte. Sem estratégia ativa, o Lastro continua monitorando a carteira,
        mas não sugere aportes. O rebalanceamento é feito só com aportes, nunca com vendas.
      </p>
      {editing && (
        <StrategyForm
          key={editing === 'new' ? 'new' : editing.id}
          strategy={editing === 'new' ? undefined : editing}
          onDone={() => setEditing(undefined)}
        />
      )}
      <QueryState isPending={strategies.isPending} error={strategies.error} />
      <RequestError error={setActive.error ?? deleteStrategy.error} />
      {strategies.data?.length === 0 && !editing && <p className="card">Nenhuma estratégia cadastrada.</p>}
      {strategies.data?.map((strategy) => (
        <article key={strategy.id} className="card stack" aria-label={strategy.name}>
          <div className="page-header">
            <h2>
              {strategy.name} {strategy.active && <span className="badge">Ativa</span>}
            </h2>
            <div className="actions">
              <button
                type="button"
                className="button button-ghost button-small"
                onClick={() => setActive.mutate({ id: strategy.id, active: !strategy.active })}
                disabled={setActive.isPending}
              >
                {strategy.active ? 'Desativar' : 'Ativar'}
              </button>
              <button type="button" className="button button-ghost button-small" onClick={() => setEditing(strategy)}>
                Editar
              </button>
              <button
                type="button"
                className="button button-ghost button-small"
                onClick={() => {
                  if (window.confirm(`Excluir a estratégia "${strategy.name}"?`)) deleteStrategy.mutate(strategy.id)
                }}
              >
                Excluir
              </button>
            </div>
          </div>
          <div className="table-wrapper">
            <table>
              <thead>
                <tr>
                  <th>Alvo</th>
                  <th className="number">%</th>
                  <th className="number">Faixa</th>
                  <th className="number">Limite por ativo</th>
                </tr>
              </thead>
              <tbody>
                {strategy.targets.map((target, index) => (
                  <tr key={index}>
                    <td>{target.assetId ? catalog.assetCode(target.assetId) : assetClassLabels[target.assetClass!]}</td>
                    <td className="number">{percent.format(target.targetPercentage)}%</td>
                    <td className="number">
                      {percent.format(target.targetPercentage - target.minTolerance)}% a{' '}
                      {percent.format(target.targetPercentage + target.maxTolerance)}%
                    </td>
                    <td className="number">{target.maxPerAsset === null ? '—' : `${percent.format(target.maxPerAsset)}%`}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </article>
      ))}
    </section>
  )
}
