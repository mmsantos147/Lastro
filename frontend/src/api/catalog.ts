import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { CurrencyCode } from '../lib/format'
import type { AssetClass, CorporateActionType, FixedIncomeIndexer, InstitutionType } from '../lib/labels'
import { apiJson, apiSend } from './http'

export interface Institution {
  id: number
  name: string
  type: InstitutionType
  cnpj: string | null
}

export interface Asset {
  id: number
  code: string
  name: string
  assetClass: AssetClass
  cnpj: string | null
  quoteCurrency: CurrencyCode
  active: boolean
}

export interface CorporateAction {
  id: number
  assetId: number
  type: CorporateActionType
  recordDate: string
  factor: number
  assignedCost: number | null
  /** Asset received in a merger; null for other types. */
  successorAssetId: number | null
}

/** Conditions of a private fixed income asset, which give its value on the curve. */
export interface FixedIncomeTerms {
  assetId: number
  issuer: string | null
  indexer: FixedIncomeIndexer
  /** Only for CDI and Selic (110 = 110% do CDI). */
  indexerPercentage: number | null
  /** Annual %: fixed rate, real rate over IPCA, or spread over CDI/Selic. */
  rate: number
  maturityDate: string | null
  dailyLiquidity: boolean
}

export type NewInstitution = Omit<Institution, 'id'>
export type NewAsset = Omit<Asset, 'id'>
export type NewCorporateAction = Omit<CorporateAction, 'id'>

export const catalogKeys = {
  institutions: ['institutions'] as const,
  assets: ['assets'] as const,
  corporateActions: ['corporate-actions'] as const,
  fixedIncome: ['fixed-income'] as const,
}

export function useFixedIncomeTerms() {
  return useQuery({
    queryKey: catalogKeys.fixedIncome,
    queryFn: () => apiJson<FixedIncomeTerms[]>('/api/fixed-income'),
  })
}

/** Saving the terms recomputes the curve: quotes and every valuation change. */
export function useSaveFixedIncomeTerms() {
  const queryClient = useQueryClient()
  const invalidatePortfolio = useInvalidatePortfolio()
  return useMutation({
    mutationFn: ({ assetId, ...terms }: FixedIncomeTerms) =>
      apiSend<FixedIncomeTerms>(`/api/fixed-income/${assetId}`, 'PUT', terms),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: catalogKeys.fixedIncome })
      void queryClient.invalidateQueries({ queryKey: ['market'] })
      void invalidatePortfolio()
    },
  })
}

export function useInstitutions() {
  return useQuery({ queryKey: catalogKeys.institutions, queryFn: () => apiJson<Institution[]>('/api/institutions') })
}

export function useAssets() {
  return useQuery({ queryKey: catalogKeys.assets, queryFn: () => apiJson<Asset[]>('/api/assets') })
}

export function useCorporateActions() {
  return useQuery({
    queryKey: catalogKeys.corporateActions,
    queryFn: () => apiJson<CorporateAction[]>('/api/corporate-actions'),
  })
}

/** Everything derived from the history (positions, summaries) must be refetched after a change. */
export function useInvalidatePortfolio() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: ['portfolio'] })
}

export function useCreateInstitution() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (institution: NewInstitution) => apiSend<Institution>('/api/institutions', 'POST', institution),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: catalogKeys.institutions }),
  })
}

export function useSaveAsset() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, asset }: { id?: number; asset: NewAsset }) =>
      id === undefined ? apiSend<Asset>('/api/assets', 'POST', asset) : apiSend<Asset>(`/api/assets/${id}`, 'PUT', asset),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: catalogKeys.assets }),
  })
}

export function useCreateCorporateAction() {
  const queryClient = useQueryClient()
  const invalidatePortfolio = useInvalidatePortfolio()
  return useMutation({
    mutationFn: (action: NewCorporateAction) => apiSend<CorporateAction>('/api/corporate-actions', 'POST', action),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: catalogKeys.corporateActions })
      void invalidatePortfolio()
    },
  })
}

export function useDeleteCorporateAction() {
  const queryClient = useQueryClient()
  const invalidatePortfolio = useInvalidatePortfolio()
  return useMutation({
    mutationFn: (id: number) => apiSend<void>(`/api/corporate-actions/${id}`, 'DELETE'),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: catalogKeys.corporateActions })
      void invalidatePortfolio()
    },
  })
}
