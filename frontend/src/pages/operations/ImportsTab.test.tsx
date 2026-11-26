import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import { json, mockApi, renderApp } from '../../test/render'

const matheus = { username: 'matheus', name: 'Matheus', role: 'ADMIN' }
const institutions = [
  { id: 7, name: 'Corretora', type: 'BROKER', cnpj: '11222333000181' },
  { id: 8, name: 'Binance', type: 'EXCHANGE', cnpj: null },
]
const batch = {
  id: 1,
  institutionId: 7,
  source: 'B3_IMPORT',
  fileName: 'movimentacao.xlsx',
  importedAt: '2026-11-24T12:00:00Z',
  status: 'COMPLETED_WITH_ISSUES',
  processedRows: 3,
  skippedRows: 2,
}
const result = {
  batch,
  trades: 2,
  distributions: 1,
  corporateActions: 0,
  duplicates: 0,
  issues: [
    {
      line: 5,
      code: 'MERGER_REQUIRED',
      needsAttention: true,
      description: '06/01/2026 · Incorporação · AUAU3 · 100',
      detail: null,
    },
    {
      line: 9,
      code: 'INFORMATIONAL',
      needsAttention: false,
      description: '17/09/2026 · Transferência · HGLG11 · 1',
      detail: null,
    },
  ],
}

describe('ImportsTab', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=token-123; path=/'
  })

  it('uploads the file with the source and institution and reports what needs attention', async () => {
    const fetchMock = mockApi({
      'GET /api/auth/me': () => json(matheus),
      'GET /api/assets': () => json([]),
      'GET /api/institutions': () => json(institutions),
      'GET /api/imports': () => json([]),
      'POST /api/imports': () => json(result, 201),
      'POST /api/market/refresh': () => json({ quotes: 10, indicatorValues: 5, failures: [] }),
    })
    renderApp('/operacoes?aba=importar')
    const form = await screen.findByRole('form', { name: 'Importar arquivo' })
    await within(form).findByRole('option', { name: 'Corretora' })
    expect(within(form).queryByRole('option', { name: 'Binance' })).not.toBeInTheDocument()

    await userEvent.selectOptions(within(form).getByLabelText('Instituição'), '7')
    const file = new File(['xlsx'], 'movimentacao.xlsx')
    await userEvent.upload(within(form).getByLabelText('Arquivo'), file)
    await userEvent.click(within(form).getByRole('button', { name: 'Importar' }))

    const report = await screen.findByRole('region', { name: 'Resultado da importação' })
    expect(report).toHaveTextContent('2 operações, 1 proventos e 0 eventos corporativos importados')
    expect(report).toHaveTextContent('Cadastre a incorporação em Eventos corporativos')
    expect(report).toHaveTextContent('AUAU3 · 100')
    const call = fetchMock.mock.calls.find(([url, init]) => url === '/api/imports' && init?.method === 'POST')!
    const body = call[1]!.body as FormData
    expect(body.get('source')).toBe('B3_IMPORT')
    expect(body.get('institutionId')).toBe('7')
    expect((body.get('file') as File).name).toBe('movimentacao.xlsx')
    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => url === '/api/market/refresh')).toBe(true))
  })

  it('offers only exchanges for Binance files', async () => {
    mockApi({
      'GET /api/auth/me': () => json(matheus),
      'GET /api/assets': () => json([]),
      'GET /api/institutions': () => json(institutions),
      'GET /api/imports': () => json([batch]),
    })
    renderApp('/operacoes?aba=importar')
    const form = await screen.findByRole('form', { name: 'Importar arquivo' })
    await within(form).findByRole('option', { name: 'Corretora' })

    await userEvent.selectOptions(within(form).getByLabelText('Origem'), 'BINANCE_CSV')

    expect(within(form).getByRole('option', { name: 'Binance' })).toBeInTheDocument()
    expect(within(form).queryByRole('option', { name: 'Corretora' })).not.toBeInTheDocument()
    expect(await screen.findByText('movimentacao.xlsx')).toBeInTheDocument()
  })
})
