import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { empty, json, mockApi, renderApp } from '../test/render'

describe('protected routes', () => {
  it('redirects to the login page when there is no session', async () => {
    mockApi({ 'GET /api/auth/me': () => empty(401) })

    const { router } = renderApp('/')

    expect(await screen.findByRole('button', { name: 'Entrar' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
  })

  it('shows the app to a logged-in user', async () => {
    mockApi({ 'GET /api/auth/me': () => json({ username: 'matheus', name: 'Matheus', role: 'ADMIN' }) })

    renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Visão geral' })).toBeInTheDocument()
    expect(screen.getByText('Matheus')).toBeInTheDocument()
  })

  it('reports when the server cannot be reached', async () => {
    mockApi({ 'GET /api/auth/me': () => empty(500) })

    renderApp('/')

    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível contatar o servidor.')
  })
})
