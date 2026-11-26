import type { AssetClass } from '../../lib/labels'

/** Categorical slots from index.css, in their validated order. */
export const series = {
  1: 'var(--series-1)',
  2: 'var(--series-2)',
  3: 'var(--series-3)',
  4: 'var(--series-4)',
  5: 'var(--series-5)',
  6: 'var(--series-6)',
} as const

/** Color follows the asset class (the entity), never its rank, so filters never repaint a class. */
export const assetClassColors: Record<AssetClass, string> = {
  STOCK: series[1],
  FII: series[2],
  ETF: series[3],
  TREASURY: series[4],
  CRYPTO: series[5],
  FIXED_INCOME: series[6],
}

export const axisProps = {
  stroke: 'var(--chart-axis)',
  tick: { fill: 'var(--chart-axis)', fontSize: 12 },
  tickLine: false,
} as const

export const gridProps = {
  stroke: 'var(--chart-grid)',
  vertical: false,
} as const

const monthFormat = new Intl.DateTimeFormat('pt-BR', { month: 'short', year: '2-digit', timeZone: 'UTC' })

/** ISO date or yyyy-MM → "nov. de 26". */
export function formatMonth(iso: string): string {
  const [year, month] = iso.split('-').map(Number)
  return monthFormat.format(new Date(Date.UTC(year, month - 1, 1)))
}

const compactBrl = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
  notation: 'compact',
  maximumFractionDigits: 1,
})

export function formatCompactMoney(value: number): string {
  return compactBrl.format(value)
}
