import { describe, expect, it } from 'vitest'
import { formatDate, formatMoney, parseDecimal } from './format'

describe('parseDecimal', () => {
  it.each([
    ['1.234,56', 1234.56],
    ['10,5', 10.5],
    ['10.5', 10.5],
    ['0,00012345', 0.00012345],
    ['  42 ', 42],
  ])('parses %s', (input, expected) => {
    expect(parseDecimal(input)).toBe(expected)
  })

  it.each(['', '   ', 'abc', '1,2,3'])('rejects %j', (input) => {
    expect(parseDecimal(input)).toBeUndefined()
  })
})

describe('formatters', () => {
  it('formats ISO dates without timezone shifts', () => {
    expect(formatDate('2026-01-05')).toBe('05/01/2026')
  })

  it('formats BRL amounts', () => {
    expect(formatMoney(1234.5)).toMatch(/R\$\s1\.234,50/)
  })
})
