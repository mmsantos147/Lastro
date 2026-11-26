import { useState, type FormEvent } from 'react'
import {
  indicatorLabels,
  useDeleteQuote,
  useIndicatorStatus,
  useQuotes,
  useRefreshMarketData,
  useSaveQuote,
} from '../../api/market'
import { DeleteButton } from '../../components/DeleteButton'
import { DecimalField, Field, QueryState, RequestError, SelectField } from '../../components/form'
import { formatDate, formatPrice, parseDecimal, todayIso } from '../../lib/format'
import { useCatalogLookup } from './useCatalogLookup'

const sourceLabels = { BRAPI: 'brapi', BINANCE: 'Binance', MANUAL: 'Manual', CURVE: 'Curva (renda fixa)' } as const

/** Market data status, manual refresh and manual quotes (for assets without an automatic source). */
export function QuotesTab() {
  const indicators = useIndicatorStatus()
  const refresh = useRefreshMarketData()

  return (
    <div className="stack">
      <section className="card stack">
        <div className="page-header">
          <div>
            <h2>Dados de mercado</h2>
            <p className="muted">
              Atualizados automaticamente às 9h e às 21h. Ações, FIIs e ETFs vêm do brapi; cripto, da Binance;
              indicadores, do Banco Central.
            </p>
          </div>
          <button type="button" className="button" onClick={() => refresh.mutate()} disabled={refresh.isPending}>
            {refresh.isPending ? 'Atualizando…' : 'Atualizar agora'}
          </button>
        </div>
        <RequestError error={refresh.error} />
        {refresh.data && (
          <p role="status">
            {refresh.data.quotes} cotações e {refresh.data.indicatorValues} valores de indicadores atualizados.
            {refresh.data.failures.length > 0 && (
              <span className="error"> Falharam: {refresh.data.failures.join(', ')}.</span>
            )}
          </p>
        )}
        <QueryState isPending={indicators.isPending} error={indicators.error} />
        {indicators.data && (
          <dl className="indicator-list">
            {indicators.data.map((indicator) => (
              <div key={indicator.type}>
                <dt>{indicatorLabels[indicator.type]}</dt>
                <dd>{indicator.latestDate ? `até ${formatDate(indicator.latestDate)}` : 'sem dados'}</dd>
              </div>
            ))}
          </dl>
        )}
      </section>
      <ManualQuotes />
    </div>
  )
}

function ManualQuotes() {
  const catalog = useCatalogLookup()
  const [assetId, setAssetId] = useState<number>()
  const [date, setDate] = useState(todayIso())
  const [price, setPrice] = useState('')
  const [invalid, setInvalid] = useState<string>()
  const quotes = useQuotes(assetId)
  const saveQuote = useSaveQuote(assetId)
  const deleteQuote = useDeleteQuote(assetId)
  const asset = assetId === undefined ? undefined : catalog.asset(assetId)

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const value = parseDecimal(price)
    if (value === undefined) {
      setInvalid('Informe um preço válido.')
      return
    }
    setInvalid(undefined)
    saveQuote.mutate({ date, price: value }, { onSuccess: () => setPrice('') })
  }

  return (
    <section className="stack">
      <h2>Cotações por ativo</h2>
      <p className="muted">
        Para ativos sem fonte automática (ex.: Tesouro Direto), informe o preço unitário de fechamento. Uma cotação
        manual substitui a automática do mesmo dia.
      </p>
      <form className="card form-grid" onSubmit={handleSubmit} aria-label="Cotação manual">
        <SelectField
          label="Ativo"
          value={assetId ?? ''}
          onChange={(event) => setAssetId(event.target.value ? Number(event.target.value) : undefined)}
          options={catalog.assetOptions}
          placeholder="Selecione"
          required
        />
        <Field label="Data" type="date" value={date} onChange={(event) => setDate(event.target.value)} required />
        <DecimalField
          label={`Preço de fechamento${asset ? ` (${asset.quoteCurrency})` : ''}`}
          value={price}
          onChange={(event) => setPrice(event.target.value)}
          required
        />
        <div className="form-actions">
          {invalid && <p className="error">{invalid}</p>}
          <RequestError error={saveQuote.error ?? deleteQuote.error} />
          <button type="submit" className="button" disabled={saveQuote.isPending || assetId === undefined}>
            Salvar cotação
          </button>
        </div>
      </form>
      {assetId !== undefined && <QueryState isPending={quotes.isPending} error={quotes.error} />}
      {quotes.data && (
        <div className="table-wrapper table-scroll">
          <table>
            <thead>
              <tr>
                <th>Data</th>
                <th className="number">Fechamento</th>
                <th>Fonte</th>
                <th aria-label="Ações" />
              </tr>
            </thead>
            <tbody>
              {[...quotes.data].reverse().map((quote) => (
                <tr key={quote.date}>
                  <td>{formatDate(quote.date)}</td>
                  <td className="number">{formatPrice(quote.closePrice, quote.currency)}</td>
                  <td>{sourceLabels[quote.source]}</td>
                  <td>
                    {quote.source === 'MANUAL' && (
                      <DeleteButton onConfirm={() => deleteQuote.mutate(quote.date)} disabled={deleteQuote.isPending} />
                    )}
                  </td>
                </tr>
              ))}
              {quotes.data.length === 0 && (
                <tr>
                  <td colSpan={4} className="muted">
                    Nenhuma cotação para este ativo.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
