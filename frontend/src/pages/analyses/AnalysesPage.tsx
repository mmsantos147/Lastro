import { Link } from 'react-router'
import { useAnalyses } from '../../api/analyses'
import { QueryState } from '../../components/form'
import { formatMoney } from '../../lib/format'
import { AnalyzeInClaude } from './AnalyzeInClaude'

const dateTime = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' })

export function AnalysesPage() {
  const analyses = useAnalyses()

  return (
    <section className="stack">
      <h1>Análises</h1>
      <AnalyzeInClaude />
      <QueryState isPending={analyses.isPending} error={analyses.error} />
      {analyses.data && (
        <div className="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>Data</th>
                <th>Título</th>
                <th className="number">Aporte</th>
              </tr>
            </thead>
            <tbody>
              {analyses.data.map((analysis) => (
                <tr key={analysis.id}>
                  <td>{dateTime.format(new Date(analysis.createdAt))}</td>
                  <td>
                    <Link to={`/analises/${analysis.id}`}>{analysis.title}</Link>
                  </td>
                  <td className="number">{analysis.amount === null ? '—' : formatMoney(analysis.amount)}</td>
                </tr>
              ))}
              {analyses.data.length === 0 && (
                <tr>
                  <td colSpan={3} className="muted">
                    Nenhuma análise salva ainda.
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
