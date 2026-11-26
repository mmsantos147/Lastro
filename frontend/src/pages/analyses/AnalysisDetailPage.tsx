import { Link, useNavigate, useParams } from 'react-router'
import { useAnalysis, useDeleteAnalysis } from '../../api/analyses'
import { QueryState, RequestError } from '../../components/form'
import { SafeMarkdown } from '../../components/SafeMarkdown'
import { SuggestionTable } from '../../components/SuggestionTable'
import { formatMoney } from '../../lib/format'

const dateTime = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'long', timeStyle: 'short' })

export function AnalysisDetailPage() {
  const id = Number(useParams().id)
  const analysis = useAnalysis(id)
  const deleteAnalysis = useDeleteAnalysis()
  const navigate = useNavigate()

  return (
    <section className="stack">
      <Link to="/analises">← Análises</Link>
      <QueryState isPending={analysis.isPending} error={analysis.error} />
      {analysis.data && (
        <>
          <div className="page-header">
            <div>
              <h1>{analysis.data.title}</h1>
              <p className="muted">
                {dateTime.format(new Date(analysis.data.createdAt))}
                {analysis.data.amount !== null && ` · aporte de ${formatMoney(analysis.data.amount)}`}
              </p>
            </div>
            <button
              type="button"
              className="button button-ghost"
              onClick={() => {
                if (window.confirm('Excluir esta análise?')) {
                  deleteAnalysis.mutate(id, { onSuccess: () => navigate('/analises', { replace: true }) })
                }
              }}
              disabled={deleteAnalysis.isPending}
            >
              Excluir
            </button>
          </div>
          <RequestError error={deleteAnalysis.error} />
          {analysis.data.suggestion && (
            <section className="stack" aria-label="Sugestão de aporte">
              <h2>Sugestão do motor de regras</h2>
              <SuggestionTable items={analysis.data.suggestion.items} amount={analysis.data.suggestion.amount} />
            </section>
          )}
          <article className="card">
            <SafeMarkdown>{analysis.data.text}</SafeMarkdown>
          </article>
          <p className="muted">
            Texto escrito pelo Claude. Os valores da tabela vêm do motor de regras do Lastro, não do texto.
          </p>
        </>
      )}
    </section>
  )
}
