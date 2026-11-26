import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { empty, json, mockApi, renderApp } from '../test/render'

const matheus = { username: 'matheus', name: 'Matheus', role: 'ADMIN' }

describe('LoginPage', () => {
  it('logs in with form-encoded credentials and opens the app', async () => {
    document.cookie = 'XSRF-TOKEN=token-123; path=/'
    let loggedIn = false
    const fetchMock = mockApi({
      'GET /api/auth/me': () => (loggedIn ? json(matheus) : empty(401)),
      'GET /api/portfolio/summary': () => json({ startDate: null, marketValue: 0, invested: 0 }),
      'GET /api/portfolio/history': () => json([]),
      'POST /api/auth/login': () => {
        loggedIn = true
        return empty(204)
      },
    })
    const { router } = renderApp('/login')

    await userEvent.type(await screen.findByLabelText('Usuário'), 'matheus')
    await userEvent.type(screen.getByLabelText('Senha'), 'correct horse battery')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByRole('heading', { name: 'Visão geral' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/')
    const loginCall = fetchMock.mock.calls.find(([url]) => url === '/api/auth/login')!
    const body = loginCall[1]!.body as URLSearchParams
    expect(body.get('username')).toBe('matheus')
    expect(body.get('password')).toBe('correct horse battery')
  })

  it('shows an error for invalid credentials', async () => {
    document.cookie = 'XSRF-TOKEN=token-123; path=/'
    mockApi({
      'GET /api/auth/me': () => empty(401),
      'POST /api/auth/login': () => empty(401),
    })
    renderApp('/login')

    await userEvent.type(await screen.findByLabelText('Usuário'), 'matheus')
    await userEvent.type(screen.getByLabelText('Senha'), 'wrong')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Usuário ou senha inválidos.')
  })
})
