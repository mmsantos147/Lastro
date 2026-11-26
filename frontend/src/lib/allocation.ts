import type { Position } from '../api/portfolio'
import type { AssetClass } from './labels'

export interface ClassSlice {
  assetClass: AssetClass
  value: number
  weight: number
}

/** Current allocation by asset class, largest first. */
export function allocationByClass(positions: Position[]): ClassSlice[] {
  const total = positions.reduce((sum, position) => sum + position.marketValue, 0)
  const byClass = new Map<AssetClass, number>()
  positions.forEach((position) =>
    byClass.set(position.assetClass, (byClass.get(position.assetClass) ?? 0) + position.marketValue),
  )
  return [...byClass.entries()]
    .map(([assetClass, value]) => ({ assetClass, value, weight: total ? value / total : 0 }))
    .sort((a, b) => b.value - a.value)
}
