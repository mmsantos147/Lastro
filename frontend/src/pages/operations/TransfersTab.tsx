import { useState, type FormEvent } from 'react'
import { useCreateTransfer, useDeleteTransfer, useTransfers } from '../../api/entries'
import { DeleteButton } from '../../components/DeleteButton'
import { DecimalField, Field, QueryState, RequestError, SelectField } from '../../components/form'
import { formatDate, formatQuantity, parseDecimal, todayIso } from '../../lib/format'
import { sourceLabels } from '../../lib/labels'
import { useCatalogLookup } from './useCatalogLookup'

const emptyForm = { assetId: '', fromInstitutionId: '', toInstitutionId: '', date: todayIso(), quantity: '', fee: '' }

export function TransfersTab() {
  const catalog = useCatalogLookup()
  const transfers = useTransfers()
  const createTransfer = useCreateTransfer()
  const deleteTransfer = useDeleteTransfer()
  const [form, setForm] = useState(emptyForm)
  const [invalid, setInvalid] = useState<string>()
  const set = (field: keyof typeof form) => (event: { target: { value: string } }) =>
    setForm((current) => ({ ...current, [field]: event.target.value }))

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const quantity = parseDecimal(form.quantity)
    if (quantity === undefined) {
      setInvalid('Informe uma quantidade válida.')
      return
    }
    setInvalid(undefined)
    createTransfer.mutate(
      {
        assetId: Number(form.assetId),
        fromInstitutionId: Number(form.fromInstitutionId),
        toInstitutionId: Number(form.toInstitutionId),
        date: form.date,
        quantity,
        fee: parseDecimal(form.fee) ?? 0,
      },
      { onSuccess: () => setForm(emptyForm) },
    )
  }

  return (
    <div className="stack">
      <p className="muted">
        Transferência entre lugares seus (ex.: da exchange para a carteira própria). Não altera o preço médio; a
        taxa, paga no próprio ativo, reduz a quantidade.
      </p>
      <form className="card form-grid" onSubmit={handleSubmit} aria-label="Nova transferência">
        <SelectField
          label="Ativo"
          value={form.assetId}
          onChange={set('assetId')}
          options={catalog.assetOptions}
          placeholder="Selecione"
          required
        />
        <SelectField
          label="De"
          value={form.fromInstitutionId}
          onChange={set('fromInstitutionId')}
          options={catalog.institutionOptions}
          placeholder="Selecione"
          required
        />
        <SelectField
          label="Para"
          value={form.toInstitutionId}
          onChange={set('toInstitutionId')}
          options={catalog.institutionOptions}
          placeholder="Selecione"
          required
        />
        <Field label="Data" type="date" value={form.date} onChange={set('date')} required />
        <DecimalField label="Quantidade enviada" value={form.quantity} onChange={set('quantity')} required />
        <DecimalField label="Taxa (no ativo)" value={form.fee} onChange={set('fee')} placeholder="0" />
        <div className="form-actions">
          {invalid && <p className="error">{invalid}</p>}
          <RequestError error={createTransfer.error} />
          <button type="submit" className="button" disabled={createTransfer.isPending}>
            Registrar transferência
          </button>
        </div>
      </form>

      <QueryState isPending={transfers.isPending || catalog.isPending} error={transfers.error ?? catalog.error} />
      <RequestError error={deleteTransfer.error} />
      {transfers.data && (
        <div className="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>Data</th>
                <th>Ativo</th>
                <th>De</th>
                <th>Para</th>
                <th className="number">Quantidade</th>
                <th className="number">Taxa</th>
                <th>Origem</th>
                <th aria-label="Ações" />
              </tr>
            </thead>
            <tbody>
              {[...transfers.data].reverse().map((transfer) => (
                <tr key={transfer.id}>
                  <td>{formatDate(transfer.date)}</td>
                  <td>{catalog.assetCode(transfer.assetId)}</td>
                  <td>{catalog.institutionName(transfer.fromInstitutionId)}</td>
                  <td>{catalog.institutionName(transfer.toInstitutionId)}</td>
                  <td className="number">{formatQuantity(transfer.quantity)}</td>
                  <td className="number">{formatQuantity(transfer.fee)}</td>
                  <td>{sourceLabels[transfer.source]}</td>
                  <td>
                    <DeleteButton
                      onConfirm={() => deleteTransfer.mutate(transfer.id)}
                      disabled={deleteTransfer.isPending}
                    />
                  </td>
                </tr>
              ))}
              {transfers.data.length === 0 && (
                <tr>
                  <td colSpan={8} className="muted">
                    Nenhuma transferência registrada.
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
