import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import { json, mockApi, renderApp } from '../../test/render'

const matheus = { username: 'matheus', name: 'Matheus', role: 'ADMIN' }
const cdb = {
  id: 3,
  code: 'CDB6255F9RH',
  name: 'CDB BANCO INTER S/A (CDB6255F9RH)',
  assetClass: 'FIXED_INCOME',
  cnpj: null,
  quoteCurrency: 'BRL',
  active: true,
}

describe('CatalogTab', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=token-123; path=/'
  })

  it('saves the terms of a fixed income asset typed in Brazilian notation', async () => {
    const fetchMock = mockApi({
      'GET /api/auth/me': () => json(matheus),
      'GET /api/assets': () => json([cdb]),
      'GET /api/institutions': () => json([]),
      'GET /api/fixed-income': () => json([]),
      'PUT /api/fixed-income/3': (init) => json({ assetId: 3, ...JSON.parse(init!.body as string) }),
    })
    renderApp('/operacoes?aba=cadastros')

    expect(await screen.findByText('sem condições')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Editar' }))
    const form = await screen.findByRole('form', { name: 'Condições da renda fixa' })
    const percentage = within(form).getByLabelText('Percentual do CDI (%)')
    await userEvent.clear(percentage)
    await userEvent.type(percentage, '102,5')
    await userEvent.type(within(form).getByLabelText('Vencimento'), '2027-06-10')
    await userEvent.click(within(form).getByRole('button', { name: 'Salvar condições' }))

    expect(await within(form).findByRole('status')).toHaveTextContent('curva recalculada')
    const call = fetchMock.mock.calls.find(([url, init]) => url === '/api/fixed-income/3' && init?.method === 'PUT')!
    expect(JSON.parse(call[1]!.body as string)).toEqual({
      issuer: null,
      indexer: 'CDI',
      indexerPercentage: 102.5,
      rate: 0,
      maturityDate: '2027-06-10',
      dailyLiquidity: false,
    })
  })
})
