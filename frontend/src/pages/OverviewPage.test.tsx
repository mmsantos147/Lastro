import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { json, mockApi, renderApp } from '../test/render'

const matheus = { username: 'matheus', name: 'Matheus', role: 'ADMIN' }

describe('OverviewPage', () => {
  it('shows wealth and returns compared with CDI', async () => {
    mockApi({
      'GET /api/auth/me': () => json(matheus),
      'GET /api/portfolio/summary': () =>
        json({
          startDate: '2025-01-02',
          marketValue: 12000,
          invested: 10000,
          returnSinceStart: 0.15,
          cdiSinceStart: 0.12,
          ibovSinceStart: null,
          return12Months: null,
          cdi12Months: null,
          ibov12Months: null,
        }),
      'GET /api/portfolio/history': () => json([]),
    })
    renderApp('/')

    expect(await screen.findByText('Rentabilidade desde 02/01/2025')).toBeInTheDocument()
    expect(screen.getByText('15,00%')).toBeInTheDocument()
    expect(screen.getByText(/CDI 12,00% · Ibovespa indisponível/)).toBeInTheDocument()
    expect(screen.getByText('A carteira tem menos de 12 meses.')).toBeInTheDocument()
  })

  it('invites to record the first trade when the portfolio is empty', async () => {
    mockApi({
      'GET /api/auth/me': () => json(matheus),
      'GET /api/portfolio/summary': () => json({ startDate: null, marketValue: 0, invested: 0 }),
      'GET /api/portfolio/history': () => json([]),
    })
    renderApp('/')

    expect(await screen.findByRole('link', { name: 'Registrar a primeira operação' })).toBeInTheDocument()
  })
})
