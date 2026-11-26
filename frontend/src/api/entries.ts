import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { CurrencyCode } from '../lib/format'
import type { DistributionType, EntrySource, TradeType } from '../lib/labels'
import { useInvalidatePortfolio } from './catalog'
import { apiJson, apiSend } from './http'

export interface Trade {
  id: number
  assetId: number
  institutionId: number
  type: TradeType
  date: string
  quantity: number
  unitPrice: number
  currency: CurrencyCode
  feeAmount: number | null
  feeAssetId: number | null
  feeQuantity: number | null
  source: EntrySource
  externalId: string | null
}

export interface Transfer {
  id: number
  assetId: number
  fromInstitutionId: number
  toInstitutionId: number
  date: string
  quantity: number
  fee: number
  source: EntrySource
  externalId: string | null
}

export interface Distribution {
  id: number
  assetId: number
  institutionId: number
  type: DistributionType
  recordDate: string
  paymentDate: string
  baseQuantity: number
  currency: CurrencyCode
  grossAmount: number
  withheldTax: number
  netAmount: number
  paidInAssetId: number | null
  paidQuantity: number | null
}

export type NewTrade = Omit<Trade, 'id' | 'source' | 'externalId'>
export type NewTransfer = Omit<Transfer, 'id' | 'source' | 'externalId'>
export type NewDistribution = Omit<Distribution, 'id' | 'netAmount'>

type Resource = 'trades' | 'transfers' | 'distributions'

function useEntries<T>(resource: Resource) {
  return useQuery({ queryKey: [resource], queryFn: () => apiJson<T[]>(`/api/${resource}`) })
}

function useCreateEntry<New, Saved>(resource: Resource) {
  const queryClient = useQueryClient()
  const invalidatePortfolio = useInvalidatePortfolio()
  return useMutation({
    mutationFn: (entry: New) => apiSend<Saved>(`/api/${resource}`, 'POST', entry),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: [resource] })
      void invalidatePortfolio()
    },
  })
}

function useDeleteEntry(resource: Resource) {
  const queryClient = useQueryClient()
  const invalidatePortfolio = useInvalidatePortfolio()
  return useMutation({
    mutationFn: (id: number) => apiSend<void>(`/api/${resource}/${id}`, 'DELETE'),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: [resource] })
      void invalidatePortfolio()
    },
  })
}

export const useTrades = () => useEntries<Trade>('trades')
export const useCreateTrade = () => useCreateEntry<NewTrade, Trade>('trades')
export const useDeleteTrade = () => useDeleteEntry('trades')

export const useTransfers = () => useEntries<Transfer>('transfers')
export const useCreateTransfer = () => useCreateEntry<NewTransfer, Transfer>('transfers')
export const useDeleteTransfer = () => useDeleteEntry('transfers')

export const useDistributions = () => useEntries<Distribution>('distributions')
export const useCreateDistribution = () => useCreateEntry<NewDistribution, Distribution>('distributions')
export const useDeleteDistribution = () => useDeleteEntry('distributions')
