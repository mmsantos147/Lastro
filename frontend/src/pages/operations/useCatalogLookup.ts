import { useMemo } from 'react'
import { useAssets, useInstitutions } from '../../api/catalog'

/** Assets and institutions by id, plus select options, for forms and tables that reference them. */
export function useCatalogLookup() {
  const assets = useAssets()
  const institutions = useInstitutions()

  return useMemo(() => {
    const assetList = assets.data ?? []
    const institutionList = institutions.data ?? []
    const assetById = new Map(assetList.map((asset) => [asset.id, asset]))
    const institutionById = new Map(institutionList.map((institution) => [institution.id, institution]))
    return {
      isPending: assets.isPending || institutions.isPending,
      error: assets.error ?? institutions.error,
      assets: assetList,
      institutions: institutionList,
      assetCode: (id: number | null | undefined) => (id == null ? '—' : (assetById.get(id)?.code ?? `#${id}`)),
      asset: (id: number) => assetById.get(id),
      institutionName: (id: number) => institutionById.get(id)?.name ?? `#${id}`,
      assetOptions: assetList.map((asset) => ({ value: asset.id, label: `${asset.code} — ${asset.name}` })),
      institutionOptions: institutionList.map((institution) => ({ value: institution.id, label: institution.name })),
    }
  }, [assets.data, assets.isPending, assets.error, institutions.data, institutions.isPending, institutions.error])
}
