import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import { json, mockApi, renderApp } from '../../test/render'

const matheus = { username: 'matheus', name: 'Matheus', role: 'ADMIN' }
const assets = [{ id: 1, code: 'ITSA4', name: 'Itaúsa', assetClass: 'STOCK', cnpj: null, quoteCurrency: 'BRL', active: true }]
const institutions = [{ id: 7, name: 'Corretora', type: 'BROKER', cnpj: '11222333000181' }]

describe('TradesTab', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=token-123; path=/'
  })

  it('sends quantities typed in Brazilian notation as numbers', async () => {
    const fetchMock = mockApi({
      'GET /api/auth/me': () => json(matheus),
      'GET /api/assets': () => json(assets),
      'GET /api/institutions': () => json(institutions),
      'GET /api/trades': () => json([]),
      'POST /api/trades': () => json({ id: 1 }, 201),
    })
    renderApp('/operacoes')
    const form = await screen.findByRole('form', { name: 'Nova operação' })
    await within(form).findAllByRole('option', { name: 'ITSA4 — Itaúsa' })

    await userEvent.selectOptions(within(form).getByLabelText('Ativo'), '1')
    await userEvent.selectOptions(within(form).getByLabelText('Instituição'), '7')
    await userEvent.type(within(form).getByLabelText('Quantidade'), '100')
    await userEvent.type(within(form).getByLabelText('Preço unitário'), '1.234,56')
    await userEvent.type(within(form).getByLabelText('Taxas totais'), '4,90')
    await userEvent.click(within(form).getByRole('button', { name: 'Registrar operação' }))

    await screen.findByLabelText('Quantidade')
    const call = fetchMock.mock.calls.find(([url, init]) => url === '/api/trades' && init?.method === 'POST')!
    expect(JSON.parse(call[1]!.body as string)).toMatchObject({
      assetId: 1,
      institutionId: 7,
      type: 'BUY',
      quantity: 100,
      unitPrice: 1234.56,
      currency: 'BRL',
      feeAmount: 4.9,
      feeAssetId: null,
    })
  })

  it('explains in Portuguese when a sell exceeds the position', async () => {
    mockApi({
      'GET /api/auth/me': () => json(matheus),
      'GET /api/assets': () => json(assets),
      'GET /api/institutions': () => json(institutions),
      'GET /api/trades': () => json([]),
      'POST /api/trades': () => json({ detail: 'Cannot sell 11 of asset 1: only 10 held' }, 422),
    })
    renderApp('/operacoes')
    const form = await screen.findByRole('form', { name: 'Nova operação' })
    await within(form).findAllByRole('option', { name: 'ITSA4 — Itaúsa' })

    await userEvent.selectOptions(within(form).getByLabelText('Tipo'), 'SELL')
    await userEvent.selectOptions(within(form).getByLabelText('Ativo'), '1')
    await userEvent.selectOptions(within(form).getByLabelText('Instituição'), '7')
    await userEvent.type(within(form).getByLabelText('Quantidade'), '11')
    await userEvent.type(within(form).getByLabelText('Preço unitário'), '10')
    await userEvent.click(within(form).getByRole('button', { name: 'Registrar operação' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('venda maior que a posição')
  })
})
