import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { json, mockApi, renderApp } from '../../test/render'

const matheus = { username: 'matheus', name: 'Matheus', role: 'ADMIN' }

describe('AnalysesPage', () => {
  it('copies a ready prompt for claude.ai with the contribution amount', async () => {
    mockApi({
      'GET /api/auth/me': () => json(matheus),
      'GET /api/analyses': () => json([]),
    })
    const user = userEvent.setup()
    const writeText = vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue()
    renderApp('/analises')

    await user.type(await screen.findByLabelText('Valor do aporte (R$)'), '2.500,00')
    await user.click(screen.getByRole('button', { name: 'Copiar prompt' }))

    expect(writeText).toHaveBeenCalledWith(
      expect.stringMatching(/^Use o prompt monthly_analysis do Lastro para analisar minha carteira para um aporte de R\$\s2\.500,00\.$/),
    )
    expect(await screen.findByRole('status')).toHaveTextContent('Prompt copiado')
  })

  it('shows the suggestion table from the data and the text as markdown', async () => {
    mockApi({
      'GET /api/auth/me': () => json(matheus),
      'GET /api/assets': () => json([]),
      'GET /api/analyses/5': () =>
        json({
          id: 5,
          createdAt: '2026-11-19T12:00:00Z',
          title: 'Aporte de novembro',
          amount: 1500,
          text: '## Leitura\n\nFIIs abaixo do alvo.',
          suggestion: {
            id: 9,
            createdAt: '2026-11-19T11:50:00Z',
            amount: 1500,
            items: [
              {
                assetClass: 'FII',
                assetId: 3,
                assetCode: 'HGLG11',
                assetName: 'CSHG Logística',
                suggestedAmount: 1500,
                reason: 'BELOW_TARGET',
                currentPercentage: 20,
                targetPercentage: 30,
              },
            ],
          },
        }),
    })
    renderApp('/analises/5')

    expect(await screen.findByRole('heading', { name: 'Aporte de novembro' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Leitura' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: /HGLG11/ })).toBeInTheDocument()
    expect(screen.getByText('20% → 30%')).toBeInTheDocument()
  })
})
