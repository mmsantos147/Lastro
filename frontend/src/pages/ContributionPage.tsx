import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { ApiError } from '../api/http'
import { useSuggestContribution } from '../api/strategy'
import { DecimalField, RequestError } from '../components/form'
import { SuggestionTable } from '../components/SuggestionTable'
import { parseDecimal } from '../lib/format'

/** Contribution suggestion straight from the rules engine (no LLM involved). */
export function ContributionPage() {
  const [amount, setAmount] = useState('')
  const [invalid, setInvalid] = useState<string>()
  const suggest = useSuggestContribution()
  const noStrategy = suggest.error instanceof ApiError && suggest.error.code === 'NO_ACTIVE_STRATEGY'

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const value = parseDecimal(amount)
    if (value === undefined || value <= 0) {
      setInvalid('Informe um valor positivo.')
      return
    }
    setInvalid(undefined)
    suggest.mutate(value)
  }

  return (
    <section className="stack">
      <h1>Sugestão de aporte</h1>
      <p className="muted">
        Distribui o aporte entre as classes abaixo do alvo da estratégia ativa, sem sugerir vendas. Cada cálculo fica
        salvo e pode ser comentado pelo Claude em uma análise.
      </p>
      <form className="card form-inline" onSubmit={handleSubmit} aria-label="Calcular aporte">
        <DecimalField label="Valor do aporte (R$)" value={amount} onChange={(event) => setAmount(event.target.value)} required />
        <button type="submit" className="button" disabled={suggest.isPending}>
          Calcular
        </button>
      </form>
      {invalid && <p className="error">{invalid}</p>}
      <RequestError error={suggest.error} />
      {noStrategy && (
        <Link to="/estrategia" className="button button-ghost">
          Cadastrar estratégia
        </Link>
      )}
      {suggest.data && <SuggestionTable items={suggest.data.items} amount={suggest.data.amount} />}
    </section>
  )
}
