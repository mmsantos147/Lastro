import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { ImportIssueCode, ImportSource, ImportStatus } from '../lib/labels'
import { catalogKeys, useInvalidatePortfolio } from './catalog'
import { apiFetch, apiJson, apiSend } from './http'

export interface ImportBatch {
  id: number
  institutionId: number
  source: ImportSource
  fileName: string
  importedAt: string
  status: ImportStatus
  processedRows: number
  skippedRows: number
}

export interface ImportIssue {
  line: number
  code: ImportIssueCode
  /** false for rows correctly left out (informational). */
  needsAttention: boolean
  /** Identifies the source row, e.g. "06/01/2026 · Incorporação · AUAU3 · 100". */
  description: string
  /** Complementary, in English. */
  detail: string | null
}

export interface ImportResult {
  batch: ImportBatch
  trades: number
  distributions: number
  corporateActions: number
  duplicates: number
  issues: ImportIssue[]
}

export interface NewImport {
  source: ImportSource
  institutionId: number
  file: File
}

export const importKeys = { list: ['imports'] as const }

export function useImports() {
  return useQuery({ queryKey: importKeys.list, queryFn: () => apiJson<ImportBatch[]>('/api/imports') })
}

/** An import creates trades, distributions, corporate actions and assets: everything derived from them is refetched. */
function useInvalidateImported() {
  const queryClient = useQueryClient()
  const invalidatePortfolio = useInvalidatePortfolio()
  return () => {
    for (const key of [importKeys.list, ['trades'], ['distributions'], catalogKeys.assets, catalogKeys.corporateActions]) {
      void queryClient.invalidateQueries({ queryKey: key })
    }
    void invalidatePortfolio()
  }
}

export function useUploadImport() {
  const invalidate = useInvalidateImported()
  return useMutation({
    mutationFn: async ({ source, institutionId, file }: NewImport) => {
      const body = new FormData()
      body.append('source', source)
      body.append('institutionId', String(institutionId))
      body.append('file', file)
      // No Content-Type header: the browser sets multipart/form-data with the boundary.
      const response = await apiFetch('/api/imports', { method: 'POST', body })
      return (await response.json()) as ImportResult
    },
    onSuccess: invalidate,
  })
}

export function useDeleteImport() {
  const invalidate = useInvalidateImported()
  return useMutation({
    mutationFn: (id: number) => apiSend<void>(`/api/imports/${id}`, 'DELETE'),
    onSuccess: invalidate,
  })
}
