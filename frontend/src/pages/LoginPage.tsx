import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { ApiError } from '../api/http'
import { currentUserQueryKey, login } from '../auth/api'
import { useCurrentUser } from '../auth/useCurrentUser'

function errorMessage(error: unknown): string {
  return error instanceof ApiError && error.status === 401
    ? 'Usuário ou senha inválidos.'
    : 'Não foi possível entrar. Tente novamente.'
}

export function LoginPage() {
  const { data: user } = useCurrentUser()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/'
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')

  const loginMutation = useMutation({
    mutationFn: () => login(username, password),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: currentUserQueryKey })
      navigate(from, { replace: true })
    },
  })

  if (user) return <Navigate to={from} replace />

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    loginMutation.mutate()
  }

  return (
    <main className="login">
      <form className="card login-card" onSubmit={handleSubmit}>
        <h1 className="brand">Lastro</h1>
        <label className="field">
          <span>Usuário</span>
          <input
            name="username"
            autoComplete="username"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            required
          />
        </label>
        <label className="field">
          <span>Senha</span>
          <input
            name="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
          />
        </label>
        {loginMutation.isError && (
          <p role="alert" className="error">
            {errorMessage(loginMutation.error)}
          </p>
        )}
        <button type="submit" className="button" disabled={loginMutation.isPending}>
          {loginMutation.isPending ? 'Entrando…' : 'Entrar'}
        </button>
      </form>
    </main>
  )
}
