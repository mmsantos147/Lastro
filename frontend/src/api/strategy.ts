import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { AssetClass } from '../lib/labels'
import { apiJson, apiSend } from './http'

/** Percentages in percent (25 = 25%); tolerances in percentage points. */
export interface Target {
  assetClass: AssetClass | null
  assetId: number | null
  targetPercentage: number
  minTolerance: number
  maxTolerance: number
  maxPerAsset: number | null
}

export interface Strategy {
  id: number
  name: string
  active: boolean
  targets: Target[]
}

export interface StrategyInput {
  name: string
  active?: boolean
  targets: Target[]
}

export type BucketStatus = 'BELOW' | 'WITHIN' | 'ABOVE' | 'NO_TARGET'

export interface Bucket {
  assetClass: AssetClass | null
  assetId: number | null
  assetCode: string | null
  value: number
  currentPercentage: number
  targetPercentage: number
  deviation: number
  minTolerance: number | null
  maxTolerance: number | null
  status: BucketStatus
}

export interface Allocation {
  strategy: { id: number; name: string } | null
  total: number
  buckets: Bucket[]
}

export type SuggestionReason = 'BELOW_TARGET' | 'PROPORTIONAL_TO_TARGET' | 'NEW_POSITION_IN_CLASS' | 'ASSET_CAP_REACHED'

export interface SuggestionItem {
  assetClass: AssetClass
  assetId: number | null
  assetCode: string | null
  assetName: string | null
  suggestedAmount: number
  reason: SuggestionReason
  currentPercentage: number
  targetPercentage: number
}

export interface Suggestion {
  id: number
  createdAt: string
  amount: number
  strategyId: number
  source: 'WEB' | 'MCP'
  items: SuggestionItem[]
}

export const strategyKeys = {
  list: ['strategies'] as const,
  allocation: ['portfolio', 'allocation'] as const,
  suggestion: (id: number) => ['suggestions', id] as const,
}

export function useStrategies() {
  return useQuery({ queryKey: strategyKeys.list, queryFn: () => apiJson<Strategy[]>('/api/strategies') })
}

export function useAllocation() {
  return useQuery({ queryKey: strategyKeys.allocation, queryFn: () => apiJson<Allocation>('/api/allocation') })
}

export function useSuggestion(id: number | undefined) {
  return useQuery({
    queryKey: strategyKeys.suggestion(id ?? 0),
    queryFn: () => apiJson<Suggestion>(`/api/contribution-suggestions/${id}`),
    enabled: id !== undefined,
  })
}

function useStrategyMutation<Variables>(mutationFn: (variables: Variables) => Promise<unknown>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: strategyKeys.list })
      void queryClient.invalidateQueries({ queryKey: strategyKeys.allocation })
    },
  })
}

export const useSaveStrategy = () =>
  useStrategyMutation(({ id, strategy }: { id?: number; strategy: StrategyInput }) =>
    id === undefined
      ? apiSend<Strategy>('/api/strategies', 'POST', strategy)
      : apiSend<Strategy>(`/api/strategies/${id}`, 'PUT', strategy),
  )

export const useSetStrategyActive = () =>
  useStrategyMutation(({ id, active }: { id: number; active: boolean }) =>
    apiSend<Strategy>(`/api/strategies/${id}/${active ? 'activate' : 'deactivate'}`, 'POST'),
  )

export const useDeleteStrategy = () => useStrategyMutation((id: number) => apiSend<void>(`/api/strategies/${id}`, 'DELETE'))

export function useSuggestContribution() {
  return useMutation({
    mutationFn: (amount: number) => apiSend<Suggestion>('/api/contribution-suggestions', 'POST', { amount }),
  })
}
