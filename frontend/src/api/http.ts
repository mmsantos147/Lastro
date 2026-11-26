/**
 * Error for non-2xx API responses. `status` is 0 when the request could not be made. `detail` comes from the
 * backend's problem details (RFC 9457), in English: show it only as complementary information.
 */
export class ApiError extends Error {
  readonly status: number
  readonly detail?: string
  /** Machine-readable reason sent by the backend for some errors (e.g. MISSING_MARKET_DATA). */
  readonly code?: string

  constructor(status: number, message = `HTTP ${status}`, problem: Problem = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.detail = problem.detail
    this.code = problem.code
  }
}

interface Problem {
  detail?: string
  code?: string
}

async function problemDetail(response: Response): Promise<Problem> {
  try {
    const body = (await response.json()) as Record<string, unknown>
    return {
      detail: typeof body.detail === 'string' ? body.detail : undefined,
      code: typeof body.code === 'string' ? body.code : undefined,
    }
  } catch {
    return {}
  }
}

// Spring Security (csrf.spa()): the token travels in a cookie readable by JS and must be echoed in a header.
const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

function readCookie(name: string): string | undefined {
  const prefix = `${name}=`
  const cookie = document.cookie.split('; ').find((entry) => entry.startsWith(prefix))
  return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : undefined
}

/** Returns the CSRF token, asking the backend to issue one first when the cookie is missing (e.g. after login). */
async function csrfToken(): Promise<string> {
  const existing = readCookie(CSRF_COOKIE)
  if (existing) return existing
  await fetch('/api/auth/csrf', { credentials: 'same-origin' })
  const issued = readCookie(CSRF_COOKIE)
  if (!issued) throw new ApiError(0, 'CSRF token unavailable')
  return issued
}

/** fetch for the Lastro API: sends the session cookie and the CSRF header, and throws ApiError on failure. */
export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  if (!SAFE_METHODS.has(method)) {
    headers.set(CSRF_HEADER, await csrfToken())
  }
  const response = await fetch(path, { ...init, method, headers, credentials: 'same-origin' })
  if (!response.ok) throw new ApiError(response.status, `HTTP ${response.status}`, await problemDetail(response))
  return response
}

/** Sends `body` as JSON and parses the JSON response (or returns undefined for 204). */
export async function apiSend<T>(path: string, method: string, body?: unknown): Promise<T> {
  const response = await apiFetch(path, {
    method,
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  return (response.status === 204 ? undefined : await response.json()) as T
}

/** Message in Portuguese for a failed request. */
export function errorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) return 'Algo deu errado. Tente novamente.'
  if (error.code === 'NO_ACTIVE_STRATEGY') {
    return 'Não há estratégia ativa, então não existe alocação alvo para calcular a sugestão. Cadastre uma em Estratégia.'
  }
  if (error.code === 'UNREADABLE_FILE') {
    return 'O arquivo não está no formato esperado. Confira se escolheu a origem certa e o arquivo exportado sem alterações.'
  }
  if (error.code === 'MISSING_MARKET_DATA') {
    return 'Faltam cotações ou a taxa do dólar para converter operações. Atualize os dados de mercado em Operações → Cotações.'
  }
  switch (error.status) {
    case 0:
      return 'Não foi possível contatar o servidor.'
    case 400:
      return 'Dados inválidos. Confira os campos.'
    case 404:
      return 'Registro não encontrado.'
    case 409:
      return 'Já existe um registro com esses dados.'
    case 422:
      return 'A operação deixaria o histórico inconsistente (ex.: venda maior que a posição na data).'
    default:
      return 'Algo deu errado no servidor. Tente novamente.'
  }
}

export async function apiJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await apiFetch(path, init)
  return (await response.json()) as T
}
