import { useState, type FormEvent } from 'react'
import { useSaveFixedIncomeTerms, type FixedIncomeTerms } from '../../api/catalog'
import { DecimalField, Field, RequestError, SelectField } from '../../components/form'
import { parseDecimal } from '../../lib/format'
import { fixedIncomeIndexerLabels, options, type FixedIncomeIndexer } from '../../lib/labels'

const rateLabels: Record<FixedIncomeIndexer, string> = {
  CDI: 'Taxa adicional ao ano (%, opcional)',
  SELIC: 'Taxa adicional ao ano (%, opcional)',
  IPCA: 'Taxa real ao ano (%)',
  PREFIXED: 'Taxa ao ano (%)',
}

function decimalText(value: number | null | undefined): string {
  return value === null || value === undefined ? '' : String(value).replace('.', ',')
}

/**
 * Conditions of a CDB/LCI/LCA, from the bank's statement. They give the value on the curve; without them the
 * security is valued at the price paid.
 */
export function FixedIncomeTermsForm({ assetId, current }: { assetId: number; current?: FixedIncomeTerms }) {
  const saveTerms = useSaveFixedIncomeTerms()
  const [form, setForm] = useState({
    issuer: current?.issuer ?? '',
    indexer: current?.indexer ?? ('CDI' as FixedIncomeIndexer),
    indexerPercentage: decimalText(current?.indexerPercentage ?? 100),
    rate: decimalText(current?.rate),
    maturityDate: current?.maturityDate ?? '',
    dailyLiquidity: current?.dailyLiquidity ?? false,
  })
  const [invalid, setInvalid] = useState<string>()
  const followsIndex = form.indexer === 'CDI' || form.indexer === 'SELIC'
  const set = (field: 'issuer' | 'indexerPercentage' | 'rate' | 'maturityDate') =>
    (event: { target: { value: string } }) => setForm((existing) => ({ ...existing, [field]: event.target.value }))

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const percentage = parseDecimal(form.indexerPercentage)
    const rate = form.rate.trim() ? parseDecimal(form.rate) : 0
    if ((followsIndex && (percentage === undefined || percentage <= 0)) || rate === undefined || rate < 0) {
      setInvalid(followsIndex ? 'Informe o percentual do índice e uma taxa válida.' : 'Informe uma taxa válida.')
      return
    }
    if (!followsIndex && rate === 0) {
      setInvalid('Informe a taxa ao ano.')
      return
    }
    setInvalid(undefined)
    saveTerms.mutate({
      assetId,
      issuer: form.issuer.trim() || null,
      indexer: form.indexer,
      indexerPercentage: followsIndex ? percentage! : null,
      rate,
      maturityDate: form.maturityDate || null,
      dailyLiquidity: form.dailyLiquidity,
    })
  }

  return (
    <form className="card form-grid" onSubmit={handleSubmit} aria-label="Condições da renda fixa">
      <p className="muted form-wide">
        Copie do extrato do banco. Com elas, o título é marcado na curva (valor bruto, antes do IR) a partir da data
        e do preço da aplicação.
      </p>
      <SelectField
        label="Indexador"
        value={form.indexer}
        onChange={(event) => setForm((existing) => ({ ...existing, indexer: event.target.value as FixedIncomeIndexer }))}
        options={options(fixedIncomeIndexerLabels)}
      />
      {followsIndex && (
        <DecimalField
          label={`Percentual do ${form.indexer === 'CDI' ? 'CDI' : 'Selic'} (%)`}
          value={form.indexerPercentage}
          onChange={set('indexerPercentage')}
          hint="110 = 110% do CDI."
          required
        />
      )}
      <DecimalField label={rateLabels[form.indexer]} value={form.rate} onChange={set('rate')} required={!followsIndex} />
      <Field label="Vencimento" type="date" value={form.maturityDate} onChange={set('maturityDate')} />
      <Field label="Emissor (opcional)" value={form.issuer} onChange={set('issuer')} />
      <label className="checkbox">
        <input
          type="checkbox"
          checked={form.dailyLiquidity}
          onChange={(event) => setForm((existing) => ({ ...existing, dailyLiquidity: event.target.checked }))}
        />
        Liquidez diária
      </label>
      <div className="form-actions">
        {invalid && <p className="error">{invalid}</p>}
        <RequestError error={saveTerms.error} />
        {saveTerms.isSuccess && <p role="status">Condições salvas e curva recalculada.</p>}
        <button type="submit" className="button" disabled={saveTerms.isPending}>
          Salvar condições
        </button>
      </div>
    </form>
  )
}
