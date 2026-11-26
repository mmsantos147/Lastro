import { useState, type FormEvent } from 'react'
import { useCreateDistribution, useDeleteDistribution, useDistributions } from '../../api/entries'
import { DeleteButton } from '../../components/DeleteButton'
import { DecimalField, Field, QueryState, RequestError, SelectField } from '../../components/form'
import { formatDate, formatMoney, formatQuantity, parseDecimal, todayIso, type CurrencyCode } from '../../lib/format'
import { distributionTypeLabels, options, type DistributionType } from '../../lib/labels'
import { useCatalogLookup } from './useCatalogLookup'

const emptyForm = {
  assetId: '',
  institutionId: '',
  type: 'DIVIDEND' as DistributionType,
  recordDate: todayIso(),
  paymentDate: todayIso(),
  baseQuantity: '',
  currency: 'BRL' as CurrencyCode,
  grossAmount: '',
  withheldTax: '',
  paidInAssetId: '',
  paidQuantity: '',
}

export function DistributionsTab() {
  const catalog = useCatalogLookup()
  const distributions = useDistributions()
  const createDistribution = useCreateDistribution()
  const deleteDistribution = useDeleteDistribution()
  const [form, setForm] = useState(emptyForm)
  const [invalid, setInvalid] = useState<string>()
  const set = (field: keyof typeof form) => (event: { target: { value: string } }) =>
    setForm((current) => ({ ...current, [field]: event.target.value }))
  const paidInAsset = form.type === 'STAKING_REWARD'

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const baseQuantity = parseDecimal(form.baseQuantity)
    const grossAmount = parseDecimal(form.grossAmount)
    if (baseQuantity === undefined || grossAmount === undefined) {
      setInvalid('Informe quantidade base e valor bruto válidos.')
      return
    }
    setInvalid(undefined)
    createDistribution.mutate(
      {
        assetId: Number(form.assetId),
        institutionId: Number(form.institutionId),
        type: form.type,
        recordDate: form.recordDate,
        paymentDate: form.paymentDate,
        baseQuantity,
        currency: form.currency,
        grossAmount,
        withheldTax: parseDecimal(form.withheldTax) ?? 0,
        paidInAssetId: paidInAsset ? Number(form.paidInAssetId) : null,
        paidQuantity: paidInAsset ? (parseDecimal(form.paidQuantity) ?? null) : null,
      },
      { onSuccess: () => setForm((current) => ({ ...emptyForm, institutionId: current.institutionId })) },
    )
  }

  return (
    <div className="stack">
      <form className="card form-grid" onSubmit={handleSubmit} aria-label="Novo provento">
        <SelectField label="Tipo" value={form.type} onChange={set('type')} options={options(distributionTypeLabels)} />
        <SelectField
          label="Ativo"
          value={form.assetId}
          onChange={set('assetId')}
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
        <Field label="Data com" type="date" value={form.recordDate} onChange={set('recordDate')} required />
        <Field label="Data de pagamento" type="date" value={form.paymentDate} onChange={set('paymentDate')} required />
        <DecimalField label="Quantidade base" value={form.baseQuantity} onChange={set('baseQuantity')} required />
        <SelectField
          label="Moeda"
          value={form.currency}
          onChange={set('currency')}
          options={[
            { value: 'BRL', label: 'Real (BRL)' },
            { value: 'USD', label: 'Dólar (USD)' },
          ]}
        />
        <DecimalField label="Valor bruto" value={form.grossAmount} onChange={set('grossAmount')} required />
        <DecimalField
          label="IR retido"
          value={form.withheldTax}
          onChange={set('withheldTax')}
          placeholder="0,00"
          hint="Ex.: 15% no JCP"
        />
        {paidInAsset && (
          <>
            <SelectField
              label="Pago no ativo"
              value={form.paidInAssetId}
              onChange={set('paidInAssetId')}
              options={catalog.assetOptions}
              placeholder="Selecione"
              required
            />
            <DecimalField label="Quantidade recebida" value={form.paidQuantity} onChange={set('paidQuantity')} required />
          </>
        )}
        <div className="form-actions">
          {invalid && <p className="error">{invalid}</p>}
          <RequestError error={createDistribution.error} />
          <button type="submit" className="button" disabled={createDistribution.isPending}>
            Registrar provento
          </button>
        </div>
      </form>

      <QueryState isPending={distributions.isPending || catalog.isPending} error={distributions.error ?? catalog.error} />
      <RequestError error={deleteDistribution.error} />
      {distributions.data && (
        <div className="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>Pagamento</th>
                <th>Tipo</th>
                <th>Ativo</th>
                <th className="number">Qtd. base</th>
                <th className="number">Bruto</th>
                <th className="number">IR</th>
                <th className="number">Líquido</th>
                <th aria-label="Ações" />
              </tr>
            </thead>
            <tbody>
              {[...distributions.data].reverse().map((distribution) => (
                <tr key={distribution.id}>
                  <td>{formatDate(distribution.paymentDate)}</td>
                  <td>{distributionTypeLabels[distribution.type]}</td>
                  <td>{catalog.assetCode(distribution.assetId)}</td>
                  <td className="number">{formatQuantity(distribution.baseQuantity)}</td>
                  <td className="number">{formatMoney(distribution.grossAmount, distribution.currency)}</td>
                  <td className="number">{formatMoney(distribution.withheldTax, distribution.currency)}</td>
                  <td className="number">{formatMoney(distribution.netAmount, distribution.currency)}</td>
                  <td>
                    <DeleteButton
                      onConfirm={() => deleteDistribution.mutate(distribution.id)}
                      disabled={deleteDistribution.isPending}
                    />
                  </td>
                </tr>
              ))}
              {distributions.data.length === 0 && (
                <tr>
                  <td colSpan={8} className="muted">
                    Nenhum provento registrado.
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
