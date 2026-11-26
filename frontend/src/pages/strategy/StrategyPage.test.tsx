import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import { json, mockApi, renderApp } from '../../test/render'

const matheus = { username: 'matheus', name: 'Matheus', role: 'ADMIN' }

describe('StrategyPage', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=token-123; path=/'
  })

  it('requires targets adding up to 100% before saving', async () => {
    const fetchMock = mockApi({
      'GET /api/auth/me': () => json(matheus),
      'GET /api/strategies': () => json([]),
      'GET /api/assets': () => json([]),
      'GET /api/institutions': () => json([]),
      'POST /api/strategies': () => json({ id: 1, name: 'Renda', active: true, targets: [] }, 201),
    })
    renderApp('/estrategia')

    await userEvent.click(await screen.findByRole('button', { name: 'Nova estratégia' }))
    const form = screen.getByRole('form', { name: 'Nova estratégia' })
    await userEvent.type(within(form).getByLabelText('Nome'), 'Renda')
    await userEvent.type(within(form).getByLabelText('Alvo (%)'), '60')
    await userEvent.click(within(form).getByRole('button', { name: 'Salvar estratégia' }))

    expect(within(form).getByText('Os alvos somam 60%; precisam somar 100%.')).toBeInTheDocument()

    await userEvent.click(within(form).getByRole('button', { name: 'Adicionar alvo' }))
    const classes = within(form).getAllByLabelText('Classe')
    await userEvent.selectOptions(classes[1], 'FII')
    await userEvent.type(within(form).getAllByLabelText('Alvo (%)')[1], '40')
    await userEvent.click(within(form).getByRole('button', { name: 'Salvar estratégia' }))

    await screen.findByRole('button', { name: 'Nova estratégia' })
    const call = fetchMock.mock.calls.find(([url, init]) => url === '/api/strategies' && init?.method === 'POST')!
    expect(JSON.parse(call[1]!.body as string)).toEqual({
      name: 'Renda',
      active: true,
      targets: [
        { assetClass: 'STOCK', assetId: null, targetPercentage: 60, minTolerance: 5, maxTolerance: 5, maxPerAsset: null },
        { assetClass: 'FII', assetId: null, targetPercentage: 40, minTolerance: 5, maxTolerance: 5, maxPerAsset: null },
      ],
    })
  })
})
