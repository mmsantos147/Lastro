import { useState, type FormEvent } from 'react'
import { useCreateTrade, useDeleteTrade, useTrades, type NewTrade } from '../../api/entries'
import { DeleteButton } from '../../components/DeleteButton'
import { DecimalField, Field, QueryState, RequestError, SelectField } from '../../components/form'
import { formatDate, formatPrice, formatQuantity, parseDecimal, todayIso, type CurrencyCode } from '../../lib/format'
import { options, sourceLabels, tradeTypeLabels, type TradeType } from '../../lib/labels'
import { useCatalogLookup } from './useCatalogLookup'

const emptyForm = {
  assetId: '',
  institutionId: '',
  type: 'BUY' as TradeType,
  date: todayIso(),
  quantity: '',
  unitPrice: '',
  currency: 'BRL' as CurrencyCode,
  feeMode: 'currency' as 'currency' | 'asset',
  feeAmount: '',
  feeAssetId: '',
  feeQuantity: '',
}

export function TradesTab() {
  const catalog = useCatalogLookup()
  const trades = useTrades()
  const createTrade = useCreateTrade()
  const deleteTrade = useDeleteTrade()
  const [form, setForm] = useState(emptyForm)
  const [invalid, setInvalid] = useState<string>()
  const set = (field: keyof typeof form) => (event: { target: { value: string } }) =>
    setForm((current) => ({ ...current, [field]: event.target.value }))

  function handleAssetChange(event: { target: { value: string } }) {
    const asset = catalog.asset(Number(event.target.value))
    setForm((current) => ({ ...current, assetId: event.target.value, currency: asset?.quoteCurrency ?? current.currency }))
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const quantity = parseDecimal(form.quantity)
    const unitPrice = parseDecimal(form.unitPrice)
    if (quantity === undefined || unitPrice === undefined) {
      setInvalid('Informe quantidade e preço válidos.')
      return
    }
    const feeInAsset = form.feeMode === 'asset'
    const trade: NewTrade = {
      assetId: Number(form.assetId),
      institutionId: Number(form.institutionId),
      type: form.type,
      date: form.date,
      quantity,
      unitPrice,
      currency: form.currency,
      feeAmount: feeInAsset ? null : (parseDecimal(form.feeAmount) ?? 0),
      feeAssetId: feeInAsset ? Number(form.feeAssetId) : null,
      feeQuantity: feeInAsset ? (parseDecimal(form.feeQuantity) ?? null) : null,
    }
    setInvalid(undefined)
    createTrade.mutate(trade, {
      onSuccess: () => setForm((current) => ({ ...emptyForm, institutionId: current.institutionId, date: current.date })),
    })
  }

  return (
    <div className="stack">
      <form className="card form-grid" onSubmit={handleSubmit} aria-label="Nova operação">
        <SelectField label="Tipo" value={form.type} onChange={set('type')} options={options(tradeTypeLabels)} />
        <SelectField
          label="Ativo"
          value={form.assetId}
          onChange={handleAssetChange}
          options={catalog.assetOptions}
          placeholder="Selecione"
          required
        />
        <SelectField
          label="Instituição"
          value={form.institutionId}
          onChange={set('institutionId')}
          options={catalog.institutionOptions}
          placeholder="Selecione"
          required
        />
        <Field label="Data" type="date" value={form.date} onChange={set('date')} required />
        <DecimalField label="Quantidade" value={form.quantity} onChange={set('quantity')} required />
        <DecimalField label="Preço unitário" value={form.unitPrice} onChange={set('unitPrice')} required />
        <SelectField
          label="Moeda do preço"
          value={form.currency}
          onChange={set('currency')}
          options={[
            { value: 'BRL', label: 'Real (BRL)' },
            { value: 'USD', label: 'Dólar / USDT (USD)' },
          ]}
        />
        <SelectField
          label="Taxa paga em"
          value={form.feeMode}
          onChange={set('feeMode')}
          options={[
            { value: 'currency', label: 'Moeda do preço' },
            { value: 'asset', label: 'Outro ativo (ex.: BNB)' },
          ]}
        />
        {form.feeMode === 'currency' ? (
          <DecimalField label="Taxas totais" value={form.feeAmount} onChange={set('feeAmount')} placeholder="0,00" />
        ) : (
          <>
            <SelectField
              label="Ativo da taxa"
              value={form.feeAssetId}
              onChange={set('feeAssetId')}
              options={catalog.assetOptions}
              placeholder="Selecione"
              required
            />
            <DecimalField label="Quantidade da taxa" value={form.feeQuantity} onChange={set('feeQuantity')} required />
          </>
        )}
        <div className="form-actions">
          {invalid && <p className="error">{invalid}</p>}
          <RequestError error={createTrade.error} />
          <button type="submit" className="button" disabled={createTrade.isPending}>
            Registrar operação
          </button>
        </div>
      </form>

      <QueryState isPending={trades.isPending || catalog.isPending} error={trades.error ?? catalog.error} />
      <RequestError error={deleteTrade.error} />
      {trades.data && (
        <div className="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>Data</th>
                <th>Tipo</th>
                <th>Ativo</th>
                <th>Instituição</th>
                <th className="number">Quantidade</th>
                <th className="number">Preço</th>
                <th className="number">Taxa</th>
                <th>Origem</th>
                <th aria-label="Ações" />
              </tr>
            </thead>
            <tbody>
              {[...trades.data].reverse().map((trade) => (
                <tr key={trade.id}>
                  <td>{formatDate(trade.date)}</td>
                  <td>{tradeTypeLabels[trade.type]}</td>
                  <td>{catalog.assetCode(trade.assetId)}</td>
                  <td>{catalog.institutionName(trade.institutionId)}</td>
                  <td className="number">{formatQuantity(trade.quantity)}</td>
                  <td className="number">{formatPrice(trade.unitPrice, trade.currency)}</td>
                  <td className="number">
                    {trade.feeAssetId
                      ? `${formatQuantity(trade.feeQuantity)} ${catalog.assetCode(trade.feeAssetId)}`
                      : formatPrice(trade.feeAmount, trade.currency)}
                  </td>
                  <td>{sourceLabels[trade.source]}</td>
                  <td>
                    <DeleteButton onConfirm={() => deleteTrade.mutate(trade.id)} disabled={deleteTrade.isPending} />
                  </td>
                </tr>
              ))}
              {trades.data.length === 0 && (
                <tr>
                  <td colSpan={9} className="muted">
                    Nenhuma operação registrada.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
