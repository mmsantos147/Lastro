import { useState, type FormEvent } from 'react'
import { useCorporateActions, useCreateCorporateAction, useDeleteCorporateAction } from '../../api/catalog'
import { DeleteButton } from '../../components/DeleteButton'
import { DecimalField, Field, QueryState, RequestError, SelectField } from '../../components/form'
import { formatDate, formatPrice, formatQuantity, parseDecimal, todayIso } from '../../lib/format'
import { corporateActionTypeLabels, type CorporateActionType } from '../../lib/labels'
import { useCatalogLookup } from './useCatalogLookup'

const supportedTypes: { value: CorporateActionType; label: string }[] = [
  { value: 'STOCK_SPLIT', label: corporateActionTypeLabels.STOCK_SPLIT },
  { value: 'REVERSE_SPLIT', label: corporateActionTypeLabels.REVERSE_SPLIT },
  { value: 'BONUS_SHARES', label: corporateActionTypeLabels.BONUS_SHARES },
  { value: 'MERGER', label: `${corporateActionTypeLabels.MERGER} / troca de código` },
]

const factorHints: Partial<Record<CorporateActionType, string>> = {
  STOCK_SPLIT: 'Ações novas por ação antiga (2 = cada ação vira 2).',
  REVERSE_SPLIT: 'Ações antigas por ação nova (10 = cada 10 ações viram 1).',
  BONUS_SHARES: 'Ações bonificadas por ação (0,1 = 10%).',
  MERGER: 'Ações recebidas por ação incorporada (0,5 = cada 2 viram 1; 1 = troca de código).',
}

const emptyForm = {
  assetId: '',
  type: 'STOCK_SPLIT' as CorporateActionType,
  recordDate: todayIso(),
  factor: '',
  assignedCost: '',
  successorAssetId: '',
}

export function CorporateActionsTab() {
  const catalog = useCatalogLookup()
  const actions = useCorporateActions()
  const createAction = useCreateCorporateAction()
  const deleteAction = useDeleteCorporateAction()
  const [form, setForm] = useState(emptyForm)
  const [invalid, setInvalid] = useState<string>()
  const set = (field: keyof typeof form) => (event: { target: { value: string } }) =>
    setForm((current) => ({ ...current, [field]: event.target.value }))
  const isBonus = form.type === 'BONUS_SHARES'
  const isMerger = form.type === 'MERGER'

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const factor = parseDecimal(form.factor)
    const assignedCost = parseDecimal(form.assignedCost)
    if (factor === undefined || (isBonus && assignedCost === undefined)) {
      setInvalid(isBonus ? 'Informe fator e custo atribuído válidos.' : 'Informe um fator válido.')
      return
    }
    if (isMerger && (!form.successorAssetId || form.successorAssetId === form.assetId)) {
      setInvalid('Escolha o ativo recebido, diferente do ativo incorporado.')
      return
    }
    setInvalid(undefined)
    createAction.mutate(
      {
        assetId: Number(form.assetId),
        type: form.type,
        recordDate: form.recordDate,
        factor,
        assignedCost: isBonus ? (assignedCost ?? null) : null,
        successorAssetId: isMerger ? Number(form.successorAssetId) : null,
      },
      { onSuccess: () => setForm(emptyForm) },
    )
  }

  return (
    <div className="stack">
      <p className="muted">
        Eventos valem para quem tem o ativo no fim da data com. Frações geradas são descartadas (a B3 as leiloa). Na
        incorporação, a posição sai do ativo incorporado e o custo total passa para o ativo recebido.
      </p>
      <form className="card form-grid" onSubmit={handleSubmit} aria-label="Novo evento">
        <SelectField label="Tipo" value={form.type} onChange={set('type')} options={supportedTypes} />
        <SelectField
          label={isMerger ? 'Ativo incorporado (de)' : 'Ativo'}
          value={form.assetId}
          onChange={set('assetId')}
          options={catalog.assetOptions}
          placeholder="Selecione"
          required
        />
        {isMerger && (
          <SelectField
            label="Ativo recebido (para)"
            value={form.successorAssetId}
            onChange={set('successorAssetId')}
            options={catalog.assetOptions}
            placeholder="Selecione"
            required
          />
        )}
        <Field label="Data com" type="date" value={form.recordDate} onChange={set('recordDate')} required />
        <DecimalField label="Fator" value={form.factor} onChange={set('factor')} hint={factorHints[form.type]} required />
        {isBonus && (
          <DecimalField
            label="Custo atribuído por ação (R$)"
            value={form.assignedCost}
            onChange={set('assignedCost')}
            required
          />
        )}
        <div className="form-actions">
          {invalid && <p className="error">{invalid}</p>}
          <RequestError error={createAction.error} />
          <button type="submit" className="button" disabled={createAction.isPending}>
            Registrar evento
          </button>
        </div>
      </form>

      <QueryState isPending={actions.isPending || catalog.isPending} error={actions.error ?? catalog.error} />
      <RequestError error={deleteAction.error} />
      {actions.data && (
        <div className="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>Data com</th>
                <th>Tipo</th>
                <th>Ativo</th>
                <th className="number">Fator</th>
                <th className="number">Custo atribuído</th>
                <th aria-label="Ações" />
              </tr>
            </thead>
            <tbody>
              {[...actions.data].reverse().map((action) => (
                <tr key={action.id}>
                  <td>{formatDate(action.recordDate)}</td>
                  <td>{corporateActionTypeLabels[action.type]}</td>
                  <td>
                    {catalog.assetCode(action.assetId)}
                    {action.successorAssetId !== null && ` → ${catalog.assetCode(action.successorAssetId)}`}
                  </td>
                  <td className="number">{formatQuantity(action.factor)}</td>
                  <td className="number">{action.assignedCost === null ? '—' : formatPrice(action.assignedCost)}</td>
                  <td>
                    <DeleteButton onConfirm={() => deleteAction.mutate(action.id)} disabled={deleteAction.isPending} />
                  </td>
                </tr>
              ))}
              {actions.data.length === 0 && (
                <tr>
                  <td colSpan={6} className="muted">
                    Nenhum evento registrado.
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
