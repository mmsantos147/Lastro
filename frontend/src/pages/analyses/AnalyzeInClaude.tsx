import { useState, type FormEvent } from 'react'
import { DecimalField } from '../../components/form'
import { parseDecimal } from '../../lib/format'
import { claudePrompt } from './claudePrompt'

/**
 * "Analisar no Claude": the conversation happens on claude.ai (with the subscription), so the site only prepares
 * the prompt and copies it to the clipboard. The analysis comes back through save_analysis.
 */
export function AnalyzeInClaude() {
  const [amount, setAmount] = useState('')
  const [message, setMessage] = useState<{ text: string; error?: boolean }>()

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const value = parseDecimal(amount)
    if (value === undefined || value <= 0) {
      setMessage({ text: 'Informe um valor positivo.', error: true })
      return
    }
    const prompt = claudePrompt(value)
    try {
      await navigator.clipboard.writeText(prompt)
      setMessage({ text: 'Prompt copiado. Cole no claude.ai, numa conversa com o conector do Lastro ativo.' })
    } catch {
      setMessage({ text: `Não foi possível copiar. Copie manualmente: ${prompt}`, error: true })
    }
  }

  return (
    <form className="card stack" onSubmit={handleSubmit} aria-label="Analisar no Claude">
      <div>
        <h2>Analisar no Claude</h2>
        <p className="muted">
          A conversa acontece no claude.ai. Informe o valor do aporte: o site copia um prompt pronto, e a análise volta
          para esta tela quando o Claude a salvar.
        </p>
      </div>
      <div className="form-inline">
        <DecimalField label="Valor do aporte (R$)" value={amount} onChange={(event) => setAmount(event.target.value)} required />
        <button type="submit" className="button">
          Copiar prompt
        </button>
        <a className="button button-ghost" href="https://claude.ai/new" target="_blank" rel="noopener noreferrer">
          Abrir o claude.ai
        </a>
      </div>
      {message && (
        <p role="status" className={message.error ? 'error' : 'muted'}>
          {message.text}
        </p>
      )}
    </form>
  )
}
