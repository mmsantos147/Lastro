import { useState, type FormEvent } from 'react'
import { useCreateInstitution, useFixedIncomeTerms, useSaveAsset, type Asset, type NewAsset } from '../../api/catalog'
import { Field, QueryState, RequestError, SelectField } from '../../components/form'
import { assetClassLabels, institutionTypeLabels, options, type AssetClass, type InstitutionType } from '../../lib/labels'
import { FixedIncomeTermsForm } from './FixedIncomeTermsForm'
import { useCatalogLookup } from './useCatalogLookup'

const emptyAsset = { code: '', name: '', assetClass: 'STOCK' as AssetClass, cnpj: '', quoteCurrency: 'BRL', active: true }

/** Assets and institutions: shared catalog referenced by every entry. */
export function CatalogTab() {
  return (
    <div className="stack">
      <AssetsSection />
      <InstitutionsSection />
    </div>
  )
}

function AssetsSection() {
  const catalog = useCatalogLookup()
  const saveAsset = useSaveAsset()
  const fixedIncome = useFixedIncomeTerms()
  const termsByAsset = new Map((fixedIncome.data ?? []).map((terms) => [terms.assetId, terms]))
  const [editingId, setEditingId] = useState<number>()
  const [form, setForm] = useState(emptyAsset)
  const set = (field: keyof typeof form) => (event: { target: { value: string } }) =>
    setForm((current) => ({ ...current, [field]: event.target.value }))

  function edit(asset: Asset) {
    setEditingId(asset.id)
    setForm({ ...asset, cnpj: asset.cnpj ?? '' })
  }

  function reset() {
    setEditingId(undefined)
    setForm(emptyAsset)
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const asset: NewAsset = {
      code: form.code,
      name: form.name,
      assetClass: form.assetClass,
      cnpj: form.cnpj.trim() || null,
      quoteCurrency: form.quoteCurrency === 'USD' ? 'USD' : 'BRL',
      active: form.active,
    }
    saveAsset.mutate({ id: editingId, asset }, { onSuccess: reset })
  }

  return (
    <section className="stack">
      <h2>Ativos</h2>
      <form className="card form-grid" onSubmit={handleSubmit} aria-label={editingId ? 'Editar ativo' : 'Novo ativo'}>
        <Field
          label="Código"
          value={form.code}
          onChange={set('code')}
          placeholder="PETR4, BTC, TESOURO-IPCA-2035"
          required
        />
        <Field label="Nome" value={form.name} onChange={set('name')} required />
        <SelectField
          label="Classe"
          value={form.assetClass}
          onChange={(event) => {
            const assetClass = event.target.value as AssetClass
            setForm((current) => ({ ...current, assetClass, quoteCurrency: assetClass === 'CRYPTO' ? 'USD' : 'BRL' }))
          }}
          options={options(assetClassLabels)}
        />
        <SelectField
          label="Cotado em"
          value={form.quoteCurrency}
          onChange={set('quoteCurrency')}
          options={[
            { value: 'BRL', label: 'Real (BRL)' },
            { value: 'USD', label: 'Dólar / USDT (USD)' },
          ]}
        />
        <Field label="CNPJ (opcional)" value={form.cnpj} onChange={set('cnpj')} />
        <label className="checkbox">
          <input
            type="checkbox"
            checked={form.active}
            onChange={(event) => setForm((current) => ({ ...current, active: event.target.checked }))}
          />
          Ativo
        </label>
        <div className="form-actions">
          <RequestError error={saveAsset.error} />
          {editingId && (
            <button type="button" className="button button-ghost" onClick={reset}>
              Cancelar
            </button>
          )}
          <button type="submit" className="button" disabled={saveAsset.isPending}>
            {editingId ? 'Salvar ativo' : 'Cadastrar ativo'}
          </button>
        </div>
      </form>
      {editingId && form.assetClass === 'FIXED_INCOME' && (
        <FixedIncomeTermsForm
          key={`${editingId}-${fixedIncome.isSuccess}`}
          assetId={editingId}
          current={termsByAsset.get(editingId)}
        />
      )}
      <QueryState isPending={catalog.isPending} error={catalog.error} />
      <div className="table-wrapper">
        <table>
          <thead>
            <tr>
              <th>Código</th>
              <th>Nome</th>
              <th>Classe</th>
              <th>Moeda</th>
              <th>Situação</th>
              <th aria-label="Ações" />
            </tr>
          </thead>
          <tbody>
            {catalog.assets.map((asset) => (
              <tr key={asset.id}>
                <td>{asset.code}</td>
                <td>{asset.name}</td>
                <td>{assetClassLabels[asset.assetClass]}</td>
                <td>{asset.quoteCurrency}</td>
                <td>
                  {asset.active ? 'Ativo' : 'Inativo'}
                  {asset.assetClass === 'FIXED_INCOME' && !termsByAsset.has(asset.id) && (
                    <>
                      {' '}
                      <span className="badge">sem condições</span>
                    </>
                  )}
                </td>
                <td>
                  <button type="button" className="button button-ghost button-small" onClick={() => edit(asset)}>
                    Editar
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}

function InstitutionsSection() {
  const catalog = useCatalogLookup()
  const createInstitution = useCreateInstitution()
  const [form, setForm] = useState({ name: '', type: 'BROKER' as InstitutionType, cnpj: '' })

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    createInstitution.mutate(
      { name: form.name, type: form.type, cnpj: form.type === 'WALLET' ? null : form.cnpj.trim() || null },
      { onSuccess: () => setForm({ name: '', type: form.type, cnpj: '' }) },
    )
  }

  return (
    <section className="stack">
      <h2>Instituições</h2>
      <form className="card form-grid" onSubmit={handleSubmit} aria-label="Nova instituição">
        <Field label="Nome" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} required />
        <SelectField
          label="Tipo"
          value={form.type}
          onChange={(event) => setForm({ ...form, type: event.target.value as InstitutionType })}
          options={options(institutionTypeLabels)}
        />
        {form.type !== 'WALLET' && (
          <Field
            label={form.type === 'EXCHANGE' ? 'CNPJ (opcional)' : 'CNPJ'}
            value={form.cnpj}
            onChange={(event) => setForm({ ...form, cnpj: event.target.value })}
            required={form.type === 'BROKER' || form.type === 'BANK'}
          />
        )}
        <div className="form-actions">
          <RequestError error={createInstitution.error} />
          <button type="submit" className="button" disabled={createInstitution.isPending}>
            Cadastrar instituição
          </button>
        </div>
      </form>
      <div className="table-wrapper">
        <table>
          <thead>
            <tr>
              <th>Nome</th>
              <th>Tipo</th>
              <th>CNPJ</th>
            </tr>
          </thead>
          <tbody>
            {catalog.institutions.map((institution) => (
              <tr key={institution.id}>
                <td>{institution.name}</td>
                <td>{institutionTypeLabels[institution.type]}</td>
                <td>{institution.cnpj ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}
