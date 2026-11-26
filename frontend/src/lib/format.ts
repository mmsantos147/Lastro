const brl = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })
const usd = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'USD' })
const percent = new Intl.NumberFormat('pt-BR', { style: 'percent', minimumFractionDigits: 2, maximumFractionDigits: 2 })
const decimal = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 8 })

export type CurrencyCode = 'BRL' | 'USD'

export function formatMoney(value: number | null | undefined, currency: CurrencyCode = 'BRL'): string {
  if (value === null || value === undefined) return '—'
  return (currency === 'USD' ? usd : brl).format(value)
}

/** Unit prices may have up to 8 decimal places (crypto, average costs). */
export function formatPrice(value: number | null | undefined, currency: CurrencyCode = 'BRL'): string {
  if (value === null || value === undefined) return '—'
  const digits = Math.abs(value) >= 1 ? 2 : 8
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: digits,
  }).format(value)
}

export function formatQuantity(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : decimal.format(value)
}

/** @param value a ratio (0.05 = 5%) */
export function formatPercent(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : percent.format(value)
}

/** ISO date (yyyy-MM-dd) → dd/MM/yyyy, without timezone shifts. */
export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—'
  const [year, month, day] = iso.split('-')
  return `${day}/${month}/${year}`
}

export function todayIso(): string {
  const now = new Date()
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 10)
}

/**
 * Parses a number typed in Brazilian ("1.234,56") or plain ("1234.56") notation. Returns undefined for empty or
 * invalid input.
 */
export function parseDecimal(input: string): number | undefined {
  const trimmed = input.trim()
  if (!trimmed) return undefined
  const normalized = trimmed.includes(',') ? trimmed.replaceAll('.', '').replace(',', '.') : trimmed
  const value = Number(normalized)
  return Number.isFinite(value) ? value : undefined
}

/** CSS class for gains and losses. */
export function signClass(value: number | null | undefined): string {
  if (!value) return ''
  return value > 0 ? 'positive' : 'negative'
}
