import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { CurrencyCode } from '../lib/format'
import { apiJson, apiSend } from './http'

export type IndicatorType = 'CDI' | 'SELIC' | 'IPCA' | 'IBOV' | 'USD_BRL'

export interface IndicatorStatus {
  type: IndicatorType
  latestDate: string | null
}

export interface QuoteEntry {
  date: string
  closePrice: number
  currency: CurrencyCode
  source: 'BRAPI' | 'BINANCE' | 'MANUAL' | 'CURVE'
}

export interface RefreshResult {
  quotes: number
  indicatorValues: number
  failures: string[]
}

export const indicatorLabels: Record<IndicatorType, string> = {
  CDI: 'CDI',
  SELIC: 'Selic',
  IPCA: 'IPCA',
  IBOV: 'Ibovespa',
  USD_BRL: 'Dólar (PTAX)',
}

export const marketKeys = {
  indicators: ['market', 'indicators'] as const,
  quotes: (assetId: number) => ['market', 'quotes', assetId] as const,
}

export function useIndicatorStatus() {
  return useQuery({ queryKey: marketKeys.indicators, queryFn: () => apiJson<IndicatorStatus[]>('/api/market/indicators') })
}

export function useQuotes(assetId: number | undefined) {
  return useQuery({
    queryKey: marketKeys.quotes(assetId ?? 0),
    queryFn: () => apiJson<QuoteEntry[]>(`/api/market/assets/${assetId}/quotes`),
    enabled: assetId !== undefined,
  })
}

/** Refreshing market data changes every valuation: market and portfolio queries are refetched. */
export function useRefreshMarketData() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => apiSend<RefreshResult>('/api/market/refresh', 'POST'),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['market'] })
      void queryClient.invalidateQueries({ queryKey: ['portfolio'] })
    },
  })
}

export function useSaveQuote(assetId: number | undefined) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (quote: { date: string; price: number }) =>
      apiSend<QuoteEntry>(`/api/market/assets/${assetId}/quotes`, 'POST', quote),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['market'] })
      void queryClient.invalidateQueries({ queryKey: ['portfolio'] })
    },
  })
}

export function useDeleteQuote(assetId: number | undefined) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (date: string) => apiSend<void>(`/api/market/assets/${assetId}/quotes/${date}`, 'DELETE'),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['market'] })
      void queryClient.invalidateQueries({ queryKey: ['portfolio'] })
    },
  })
}
