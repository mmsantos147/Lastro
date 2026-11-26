import { formatMoney } from '../../lib/format'

/** The prompt pasted on claude.ai: it asks Claude to use the MCP prompt of the Lastro connector. */
export function claudePrompt(amount: number): string {
  return `Use o prompt monthly_analysis do Lastro para analisar minha carteira para um aporte de ${formatMoney(amount)}.`
}
