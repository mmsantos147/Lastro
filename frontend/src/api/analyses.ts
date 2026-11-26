import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiJson, apiSend } from './http'
import type { SuggestionItem } from './strategy'

export interface AnalysisSummary {
  id: number
  createdAt: string
  title: string
  amount: number | null
  hasSuggestion: boolean
}

export interface AnalysisDetail {
  id: number
  createdAt: string
  title: string
  amount: number | null
  /** Untrusted markdown written by the LLM: render it only with SafeMarkdown. */
  text: string
  suggestion: { id: number; createdAt: string; amount: number; items: SuggestionItem[] } | null
}

export const analysisKeys = {
  list: ['analyses'] as const,
  detail: (id: number) => ['analyses', id] as const,
}

export function useAnalyses() {
  return useQuery({ queryKey: analysisKeys.list, queryFn: () => apiJson<AnalysisSummary[]>('/api/analyses') })
}

export function useAnalysis(id: number) {
  return useQuery({ queryKey: analysisKeys.detail(id), queryFn: () => apiJson<AnalysisDetail>(`/api/analyses/${id}`) })
}

export function useDeleteAnalysis() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => apiSend<void>(`/api/analyses/${id}`, 'DELETE'),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: analysisKeys.list }),
  })
}
