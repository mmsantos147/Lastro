import { ApiError, apiFetch, apiJson } from '../api/http'

export interface CurrentUser {
  username: string
  name: string
  role: 'ADMIN' | 'USER'
}

export const currentUserQueryKey = ['auth', 'me'] as const

/** The logged-in user, or null when there is no session. */
export async function fetchCurrentUser(): Promise<CurrentUser | null> {
  try {
    return await apiJson<CurrentUser>('/api/auth/me')
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return null
    throw error
  }
}

export async function login(username: string, password: string): Promise<void> {
  // Spring Security form login expects form-encoded parameters
  await apiFetch('/api/auth/login', { method: 'POST', body: new URLSearchParams({ username, password }) })
}

export async function logout(): Promise<void> {
  await apiFetch('/api/auth/logout', { method: 'POST' })
}
