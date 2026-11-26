import { useQuery } from '@tanstack/react-query'
import type { AssetClass } from '../lib/labels'
import { apiJson } from './http'

export interface Position {
  assetId: number
  code: string
  name: string
  assetClass: AssetClass
  quantity: number
  averageCost: number
  totalCost: number
  /** Latest close in BRL; null without quotes (then marketValue = totalCost). */
  price: number | null
  priceDate: string | null
  marketValue: number
  result: number | null
  resultPercent: number | null
  weight: number
}

/** Returns are ratios (0.1 = 10%); null when unavailable (e.g. no Ibovespa data, portfolio younger than 12 months). */
export interface PortfolioSummary {
  startDate: string | null
  marketValue: number
  invested: number
  returnSinceStart: number | null
  cdiSinceStart: number | null
  ibovSinceStart: number | null
  return12Months: number | null
  cdi12Months: number | null
  ibov12Months: number | null
}

/** Indices are growth factors since the first day (1.1 = +10%). */
export interface HistoryPoint {
  date: string
  marketValue: number
  invested: number
  portfolioIndex: number
  cdiIndex: number
  ibovIndex: number | null
}

export interface Income {
  months: { month: string; net: number; movingAverage: number }[]
  lastTwelveMonths: number
  yieldOnCost: number | null
  byAsset: { assetId: number; code: string; lastTwelveMonths: number; yieldOnCost: number | null }[]
}

/** Every query derived from the history lives under ['portfolio', ...] so one invalidation refreshes all. */
export const portfolioKeys = {
  positions: ['portfolio', 'positions'] as const,
  summary: ['portfolio', 'summary'] as const,
  history: ['portfolio', 'history'] as const,
  income: (months: number) => ['portfolio', 'income', months] as const,
}

export function usePositions() {
  return useQuery({ queryKey: portfolioKeys.positions, queryFn: () => apiJson<Position[]>('/api/positions') })
}

export function useSummary() {
  return useQuery({ queryKey: portfolioKeys.summary, queryFn: () => apiJson<PortfolioSummary>('/api/portfolio/summary') })
}

export function useHistory() {
  return useQuery({ queryKey: portfolioKeys.history, queryFn: () => apiJson<HistoryPoint[]>('/api/portfolio/history') })
}

export function useIncome(months: number) {
  return useQuery({
    queryKey: portfolioKeys.income(months),
    queryFn: () => apiJson<Income>(`/api/portfolio/income?months=${months}`),
  })
}
