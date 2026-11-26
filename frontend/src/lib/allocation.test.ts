import { describe, expect, it } from 'vitest'
import type { Position } from '../api/portfolio'
import { allocationByClass } from './allocation'

const position = (assetClass: Position['assetClass'], marketValue: number): Position => ({
  assetId: Math.random(),
  code: 'X',
  name: 'X',
  assetClass,
  quantity: 1,
  averageCost: 1,
  totalCost: 1,
  price: null,
  priceDate: null,
  marketValue,
  result: null,
  resultPercent: null,
  weight: 0,
})

describe('allocationByClass', () => {
  it('sums market values per class, largest first', () => {
    const slices = allocationByClass([position('FII', 100), position('STOCK', 250), position('FII', 50)])

    expect(slices).toEqual([
      { assetClass: 'STOCK', value: 250, weight: 0.625 },
      { assetClass: 'FII', value: 150, weight: 0.375 },
    ])
  })

  it('returns nothing for an empty portfolio', () => {
    expect(allocationByClass([])).toEqual([])
  })
})
