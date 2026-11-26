import type { SuggestionItem } from '../api/strategy'
import { formatMoney } from '../lib/format'
import { assetClassLabels, suggestionReasonLabels } from '../lib/labels'

const percent = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 })

/** Items of a contribution suggestion. The numbers always come from the rules engine, never from the LLM. */
export function SuggestionTable({ items, amount }: { items: SuggestionItem[]; amount: number }) {
  return (
    <div className="table-wrapper">
      <table>
        <thead>
          <tr>
            <th>Classe / ativo</th>
            <th className="number">Valor sugerido</th>
            <th className="number">Atual → alvo</th>
            <th>Motivo</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item, index) => (
            <tr key={index}>
              <td>
                {item.assetCode ? (
                  <>
                    <strong>{item.assetCode}</strong> <span className="muted">{assetClassLabels[item.assetClass]}</span>
                  </>
                ) : (
                  <strong>{assetClassLabels[item.assetClass]}</strong>
                )}
              </td>
              <td className="number">{formatMoney(item.suggestedAmount)}</td>
              <td className="number">
                {percent.format(item.currentPercentage)}% → {percent.format(item.targetPercentage)}%
              </td>
              <td>{suggestionReasonLabels[item.reason]}</td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr>
            <th>Total</th>
            <th className="number">{formatMoney(amount)}</th>
            <th colSpan={2} />
          </tr>
        </tfoot>
      </table>
    </div>
  )
}
