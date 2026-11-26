import { useState } from 'react'
import { usePositions } from '../api/portfolio'
import { QueryState } from '../components/form'
import { formatDate, formatMoney, formatPercent, formatPrice, formatQuantity, signClass } from '../lib/format'
import { assetClassLabels, options, type AssetClass } from '../lib/labels'

export function PositionsPage() {
  const positions = usePositions()
  const [assetClass, setAssetClass] = useState<AssetClass | ''>('')
  const visible = (positions.data ?? []).filter((position) => !assetClass || position.assetClass === assetClass)
  const totals = visible.reduce(
    (sum, position) => ({ cost: sum.cost + position.totalCost, value: sum.value + position.marketValue }),
    { cost: 0, value: 0 },
  )

  return (
    <section className="stack">
      <div className="page-header">
        <h1>Posições</h1>
        <label className="field field-inline">
          <span>Classe</span>
          <select value={assetClass} onChange={(event) => setAssetClass(event.target.value as AssetClass | '')}>
            <option value="">Todas</option>
            {options(assetClassLabels).map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
      </div>
      <QueryState isPending={positions.isPending} error={positions.error} />
      {positions.data && (
        <div className="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>Ativo</th>
                <th>Classe</th>
                <th className="number">Quantidade</th>
                <th className="number">Preço médio</th>
                <th className="number">Cotação</th>
                <th className="number">Custo total</th>
                <th className="number">Valor atual</th>
                <th className="number">Resultado</th>
                <th className="number">Peso</th>
              </tr>
            </thead>
            <tbody>
              {visible.map((position) => (
                <tr key={position.assetId}>
                  <td className="wrap">
                    <strong>{position.code}</strong> <span className="muted">{position.name}</span>
                  </td>
                  <td>{assetClassLabels[position.assetClass]}</td>
                  <td className="number">{formatQuantity(position.quantity)}</td>
                  <td className="number">{formatPrice(position.averageCost)}</td>
                  <td className="number" title={position.priceDate ? `Fechamento de ${formatDate(position.priceDate)}` : undefined}>
                    {position.price === null ? <span className="muted">sem cotação</span> : formatPrice(position.price)}
                  </td>
                  <td className="number">{formatMoney(position.totalCost)}</td>
                  <td className="number">{formatMoney(position.marketValue)}</td>
                  <td className={`number ${signClass(position.result)}`}>
                    {position.result === null ? '—' : `${formatMoney(position.result)} (${formatPercent(position.resultPercent)})`}
                  </td>
                  <td className="number">{formatPercent(position.weight)}</td>
                </tr>
              ))}
              {visible.length === 0 && (
                <tr>
                  <td colSpan={9} className="muted">
                    Nenhuma posição aberta.
                  </td>
                </tr>
              )}
            </tbody>
            {visible.length > 0 && (
              <tfoot>
                <tr>
                  <th colSpan={5}>Total</th>
                  <th className="number">{formatMoney(totals.cost)}</th>
                  <th className="number">{formatMoney(totals.value)}</th>
                  <th className={`number ${signClass(totals.value - totals.cost)}`}>
                    {formatMoney(totals.value - totals.cost)}
                  </th>
                  <th />
                </tr>
              </tfoot>
            )}
          </table>
        </div>
      )}
    </section>
  )
}
