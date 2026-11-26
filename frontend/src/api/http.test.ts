import { describe, expect, it } from 'vitest'
import { empty, json, mockApi } from '../test/render'
import { ApiError, apiFetch, apiJson } from './http'

describe('apiFetch', () => {
  it('sends the session cookie and no CSRF header on GET', async () => {
    const fetchMock = mockApi({ 'GET /api/things': () => json([]) })

    await apiJson('/api/things')

    const init = fetchMock.mock.calls[0][1]!
    expect(init.credentials).toBe('same-origin')
    expect(new Headers(init.headers).has('X-XSRF-TOKEN')).toBe(false)
  })

  it('echoes the CSRF cookie in the header on state-changing requests', async () => {
    document.cookie = 'XSRF-TOKEN=token-123; path=/'
    const fetchMock = mockApi({ 'POST /api/things': () => empty(204) })

    await apiFetch('/api/things', { method: 'POST' })

    expect(new Headers(fetchMock.mock.calls[0][1]!.headers).get('X-XSRF-TOKEN')).toBe('token-123')
  })

  it('asks the backend for a CSRF token when the cookie is missing', async () => {
    const fetchMock = mockApi({
      'GET /api/auth/csrf': () => {
        document.cookie = 'XSRF-TOKEN=fresh-token; path=/'
        return empty(204)
      },
      'POST /api/things': () => empty(204),
    })

    await apiFetch('/api/things', { method: 'POST' })

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(new Headers(fetchMock.mock.calls[1][1]!.headers).get('X-XSRF-TOKEN')).toBe('fresh-token')
  })

  it('throws ApiError with the status on failure', async () => {
    mockApi({ 'GET /api/things': () => empty(403) })

    await expect(apiFetch('/api/things')).rejects.toEqual(new ApiError(403))
  })
})
