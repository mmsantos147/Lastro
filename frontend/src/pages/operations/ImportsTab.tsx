import { useId, useState, type FormEvent } from 'react'
import { useDeleteImport, useImports, useUploadImport, type ImportIssue, type ImportResult } from '../../api/imports'
import { useRefreshMarketData } from '../../api/market'
import { QueryState, RequestError, SelectField } from '../../components/form'
import {
  importIssueLabels,
  importSourceLabels,
  importStatusLabels,
  options,
  type ImportIssueCode,
  type ImportSource,
  type InstitutionType,
} from '../../lib/labels'
import { useCatalogLookup } from './useCatalogLookup'

const institutionTypes: Record<ImportSource, InstitutionType[]> = {
  B3_IMPORT: ['BROKER', 'BANK'],
  BINANCE_CSV: ['EXCHANGE'],
}

const accepted: Record<ImportSource, string> = {
  B3_IMPORT: '.xlsx',
  BINANCE_CSV: '.csv,.zip',
}

const howToExport: Record<ImportSource, string> = {
  B3_IMPORT:
    'Área do Investidor da B3 → Extratos → Movimentação → filtre o período (pode ser todo o histórico) → baixe em Excel.',
  BINANCE_CSV:
    'Binance → Carteira → Histórico de transações → Exportar registros de transações. Envie o .zip recebido ou o .csv de dentro dele.',
}

const dateTime = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' })

/**
 * Upload of B3 and Binance exports. Imports are idempotent (re-importing skips what is already there), so the usual
 * flow after fixing a pending item, such as registering a merger, is to import the same file again.
 */
export function ImportsTab() {
  const catalog = useCatalogLookup()
  const imports = useImports()
  const upload = useUploadImport()
  const deleteImport = useDeleteImport()
  const refreshMarket = useRefreshMarketData()
  const [source, setSource] = useState<ImportSource>('B3_IMPORT')
  const [institutionId, setInstitutionId] = useState('')
  const [file, setFile] = useState<File>()
  const [fileInputKey, setFileInputKey] = useState(0)
  const fileInputId = useId()

  const institutionOptions = catalog.institutions
    .filter((institution) => institutionTypes[source].includes(institution.type))
    .map((institution) => ({ value: institution.id, label: institution.name }))

  function handleSourceChange(value: ImportSource) {
    setSource(value)
    setInstitutionId('')
    setFile(undefined)
    setFileInputKey((key) => key + 1)
    upload.reset()
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!file || !institutionId) return
    upload.mutate(
      { source, institutionId: Number(institutionId), file },
      {
        onSuccess: (result) => {
          setFile(undefined)
          setFileInputKey((key) => key + 1)
          // New assets need quotes (and crypto trades the dollar rate) before positions can be valued.
          if (result.trades > 0) refreshMarket.mutate()
        },
      },
    )
  }

  return (
    <div className="stack">
      <p className="muted">
        Importe os arquivos exportados pela B3 e pela Binance. Linhas já importadas são ignoradas, então você pode
        importar o mesmo arquivo (ou um mais recente, com o histórico todo) quantas vezes quiser.
      </p>
      <form className="card form-grid" onSubmit={handleSubmit} aria-label="Importar arquivo">
        <SelectField
          label="Origem"
          value={source}
          onChange={(event) => handleSourceChange(event.target.value as ImportSource)}
          options={options(importSourceLabels)}
        />
        <SelectField
          label="Instituição"
          value={institutionId}
          onChange={(event) => setInstitutionId(event.target.value)}
          options={institutionOptions}
          placeholder="Selecione"
          required
        />
        {/*
          The hint stays outside the label, so the field's accessible name is just "Arquivo". No `required`: the
          submit button is disabled until a file is chosen.
        */}
        <div className="field">
          <label htmlFor={fileInputId}>Arquivo</label>
          <input
            key={fileInputKey}
            id={fileInputId}
            type="file"
            accept={accepted[source]}
            aria-describedby={`${fileInputId}-hint`}
            onChange={(event) => setFile(event.target.files?.[0])}
          />
          <small id={`${fileInputId}-hint`} className="muted">
            {howToExport[source]}
          </small>
        </div>
        <div className="form-actions">
          {!catalog.isPending && institutionOptions.length === 0 && (
            <p className="error">
              Cadastre antes a {source === 'B3_IMPORT' ? 'corretora' : 'exchange'} na aba “Ativos e instituições”.
            </p>
          )}
          <RequestError error={upload.error} />
          <button type="submit" className="button" disabled={upload.isPending || !file || !institutionId}>
            {upload.isPending ? 'Importando…' : 'Importar'}
          </button>
        </div>
      </form>

      {upload.data && <ImportReport result={upload.data} />}
      {refreshMarket.isPending && <p role="status">Atualizando cotações dos ativos importados…</p>}
      {refreshMarket.data && refreshMarket.data.failures.length > 0 && (
        <p className="error">
          Não foi possível atualizar as cotações de: {refreshMarket.data.failures.join(', ')}. Tente de novo na aba
          Cotações.
        </p>
      )}
      <RequestError error={refreshMarket.error} />

      <section className="stack">
        <h2>Importações anteriores</h2>
        <QueryState isPending={imports.isPending} error={imports.error} />
        <RequestError error={deleteImport.error} />
        {imports.data && (
          <div className="table-wrapper">
            <table>
              <thead>
                <tr>
                  <th>Quando</th>
                  <th>Arquivo</th>
                  <th>Instituição</th>
                  <th>Situação</th>
                  <th className="number">Registros</th>
                  <th className="number">Ignorados</th>
                  <th aria-label="Ações" />
                </tr>
              </thead>
              <tbody>
                {imports.data.map((batch) => (
                  <tr key={batch.id}>
                    <td>{dateTime.format(new Date(batch.importedAt))}</td>
                    <td>{batch.fileName}</td>
                    <td>{catalog.institutionName(batch.institutionId)}</td>
                    <td>{importStatusLabels[batch.status]}</td>
                    <td className="number">{batch.processedRows}</td>
                    <td className="number">{batch.skippedRows}</td>
                    <td>
                      <button
                        type="button"
                        className="button button-ghost button-small"
                        disabled={deleteImport.isPending}
                        onClick={() => {
                          if (window.confirm('Desfazer esta importação? As operações e proventos criados por ela serão excluídos.')) {
                            deleteImport.mutate(batch.id)
                          }
                        }}
                      >
                        Desfazer
                      </button>
                    </td>
                  </tr>
                ))}
                {imports.data.length === 0 && (
                  <tr>
                    <td colSpan={7} className="muted">
                      Nenhum arquivo importado.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
}

function ImportReport({ result }: { result: ImportResult }) {
  const attention = result.issues.filter((issue) => issue.needsAttention)
  const informational = result.issues.filter((issue) => !issue.needsAttention)

  return (
    <section className="card stack" aria-label="Resultado da importação">
      <h2>Resultado da importação</h2>
      <p role="status">
        {result.trades} operações, {result.distributions} proventos e {result.corporateActions} eventos corporativos
        importados.
        {result.duplicates > 0 && ` ${result.duplicates} linhas já tinham sido importadas antes.`}
      </p>
      {attention.length > 0 ? (
        <div className="stack">
          <h3>Precisam de atenção ({attention.length})</h3>
          {groupByCode(attention).map(([code, issues]) => (
            <IssueGroup key={code} code={code} issues={issues} />
          ))}
        </div>
      ) : (
        <p className="positive">Nenhuma pendência.</p>
      )}
      {informational.length > 0 && (
        <details>
          <summary>Linhas ignoradas por não mudarem a posição ({informational.length})</summary>
          <IssueList issues={informational} />
        </details>
      )}
    </section>
  )
}

function IssueGroup({ code, issues }: { code: ImportIssueCode; issues: ImportIssue[] }) {
  return (
    <div>
      <p>
        <strong>{importIssueLabels[code]}</strong>
      </p>
      <IssueList issues={issues} />
    </div>
  )
}

function IssueList({ issues }: { issues: ImportIssue[] }) {
  return (
    <ul className="issue-list">
      {issues.map((issue) => (
        <li key={`${issue.line}-${issue.code}-${issue.description}`}>
          <span className="muted">Linha {issue.line}:</span> {issue.description}
          {issue.detail && <small className="muted"> — {issue.detail}</small>}
        </li>
      ))}
    </ul>
  )
}

function groupByCode(issues: ImportIssue[]): [ImportIssueCode, ImportIssue[]][] {
  const groups = new Map<ImportIssueCode, ImportIssue[]>()
  for (const issue of issues) {
    groups.set(issue.code, [...(groups.get(issue.code) ?? []), issue])
  }
  return [...groups.entries()]
}
