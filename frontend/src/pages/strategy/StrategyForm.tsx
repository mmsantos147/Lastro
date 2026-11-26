import { useState, type FormEvent } from 'react'
import { useSaveStrategy, type Strategy, type Target } from '../../api/strategy'
import { DecimalField, Field, RequestError, SelectField } from '../../components/form'
import { parseDecimal } from '../../lib/format'
import { assetClassLabels, options, type AssetClass } from '../../lib/labels'
import { useCatalogLookup } from '../operations/useCatalogLookup'

interface TargetRow {
  kind: 'class' | 'asset'
  assetClass: AssetClass
  assetId: string
  targetPercentage: string
  minTolerance: string
  maxTolerance: string
  maxPerAsset: string
}

const decimalText = (value: number | null) => (value === null ? '' : String(value).replace('.', ','))

function toRow(target: Target): TargetRow {
  return {
    kind: target.assetId === null ? 'class' : 'asset',
    assetClass: target.assetClass ?? 'STOCK',
    assetId: target.assetId === null ? '' : String(target.assetId),
    targetPercentage: decimalText(target.targetPercentage),
    minTolerance: decimalText(target.minTolerance),
    maxTolerance: decimalText(target.maxTolerance),
    maxPerAsset: decimalText(target.maxPerAsset),
  }
}

const emptyRow: TargetRow = {
  kind: 'class',
  assetClass: 'STOCK',
  assetId: '',
  targetPercentage: '',
  minTolerance: '5',
  maxTolerance: '5',
  maxPerAsset: '',
}

/** Creates or edits a strategy: name and target rows whose percentages must add up to 100. */
export function StrategyForm({ strategy, onDone }: { strategy?: Strategy; onDone: () => void }) {
  const catalog = useCatalogLookup()
  const saveStrategy = useSaveStrategy()
  const [name, setName] = useState(strategy?.name ?? '')
  const [rows, setRows] = useState<TargetRow[]>(strategy ? strategy.targets.map(toRow) : [{ ...emptyRow }])
  const [invalid, setInvalid] = useState<string>()
  const total = rows.reduce((sum, row) => sum + (parseDecimal(row.targetPercentage) ?? 0), 0)

  const update = (index: number, changes: Partial<TargetRow>) =>
    setRows((current) => current.map((row, i) => (i === index ? { ...row, ...changes } : row)))

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (Math.abs(total - 100) > 1e-9) {
      setInvalid(`Os alvos somam ${total.toLocaleString('pt-BR')}%; precisam somar 100%.`)
      return
    }
    const targets: Target[] = rows.map((row) => ({
      assetClass: row.kind === 'class' ? row.assetClass : null,
      assetId: row.kind === 'asset' ? Number(row.assetId) : null,
      targetPercentage: parseDecimal(row.targetPercentage) ?? 0,
      minTolerance: parseDecimal(row.minTolerance) ?? 0,
      maxTolerance: parseDecimal(row.maxTolerance) ?? 0,
      maxPerAsset: row.kind === 'class' ? (parseDecimal(row.maxPerAsset) ?? null) : null,
    }))
    setInvalid(undefined)
    saveStrategy.mutate(
      { id: strategy?.id, strategy: { name, active: strategy ? undefined : true, targets } },
      { onSuccess: onDone },
    )
  }

  return (
    <form className="card stack" onSubmit={handleSubmit} aria-label={strategy ? 'Editar estratégia' : 'Nova estratégia'}>
      <Field label="Nome" value={name} onChange={(event) => setName(event.target.value)} required />
      <p className="muted">
        Cada alvo é uma classe ou um ativo específico (que sai da sua classe). Tolerância em pontos percentuais em
        torno do alvo; o limite por ativo vale para cada ativo da classe, em % da carteira.
      </p>
      {rows.map((row, index) => (
        <fieldset key={index} className="target-row">
          <legend className="sr-only">Alvo {index + 1}</legend>
          <SelectField
            label="Tipo"
            value={row.kind}
            onChange={(event) => update(index, { kind: event.target.value as TargetRow['kind'] })}
            options={[
              { value: 'class', label: 'Classe' },
              { value: 'asset', label: 'Ativo' },
            ]}
          />
          {row.kind === 'class' ? (
            <SelectField
              label="Classe"
              value={row.assetClass}
              onChange={(event) => update(index, { assetClass: event.target.value as AssetClass })}
              options={options(assetClassLabels)}
            />
          ) : (
            <SelectField
              label="Ativo"
              value={row.assetId}
              onChange={(event) => update(index, { assetId: event.target.value })}
              options={catalog.assetOptions}
              placeholder="Selecione"
              required
            />
          )}
          <DecimalField
            label="Alvo (%)"
            value={row.targetPercentage}
            onChange={(event) => update(index, { targetPercentage: event.target.value })}
            required
          />
          <DecimalField
            label="Tolerância abaixo (p.p.)"
            value={row.minTolerance}
            onChange={(event) => update(index, { minTolerance: event.target.value })}
          />
          <DecimalField
            label="Tolerância acima (p.p.)"
            value={row.maxTolerance}
            onChange={(event) => update(index, { maxTolerance: event.target.value })}
          />
          {row.kind === 'class' && (
            <DecimalField
              label="Limite por ativo (%)"
              value={row.maxPerAsset}
              onChange={(event) => update(index, { maxPerAsset: event.target.value })}
              placeholder="sem limite"
            />
          )}
          <button
            type="button"
            className="button button-ghost button-small target-remove"
            onClick={() => setRows((current) => current.filter((_, i) => i !== index))}
            disabled={rows.length === 1}
          >
            Remover
          </button>
        </fieldset>
      ))}
      <div className="form-actions">
        <span className={Math.abs(total - 100) > 1e-9 ? 'error' : 'muted'}>Soma: {total.toLocaleString('pt-BR')}%</span>
        {invalid && <p className="error">{invalid}</p>}
        <RequestError error={saveStrategy.error} />
        <button type="button" className="button button-ghost" onClick={() => setRows((current) => [...current, { ...emptyRow }])}>
          Adicionar alvo
        </button>
        <button type="button" className="button button-ghost" onClick={onDone}>
          Cancelar
        </button>
        <button type="submit" className="button" disabled={saveStrategy.isPending}>
          Salvar estratégia
        </button>
      </div>
    </form>
  )
}
