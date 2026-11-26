import { useMutation, useQueryClient } from '@tanstack/react-query'
import { NavLink, Outlet, useNavigate } from 'react-router'
import { currentUserQueryKey, logout } from '../auth/api'
import { useCurrentUser } from '../auth/useCurrentUser'
import { ThemeToggle } from '../theme/ThemeToggle'

export function Layout() {
  const { data: user } = useCurrentUser()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const logoutMutation = useMutation({
    mutationFn: logout,
    onSuccess: () => {
      queryClient.clear()
      queryClient.setQueryData(currentUserQueryKey, null)
      navigate('/login', { replace: true })
    },
  })

  return (
    <div className="app">
      <header className="app-header">
        <span className="brand">Lastro</span>
        <nav className="app-nav" aria-label="Principal">
          <NavLink to="/" end>
            Visão geral
          </NavLink>
          <NavLink to="/alocacao">Alocação</NavLink>
          <NavLink to="/proventos">Proventos</NavLink>
          <NavLink to="/posicoes">Posições</NavLink>
          <NavLink to="/operacoes">Operações</NavLink>
          <NavLink to="/aporte">Aporte</NavLink>
          <NavLink to="/estrategia">Estratégia</NavLink>
          <NavLink to="/analises">Análises</NavLink>
        </nav>
        <div className="app-user">
          <span className="muted">{user?.name}</span>
          <ThemeToggle />
          <button
            type="button"
            className="button button-ghost"
            onClick={() => logoutMutation.mutate()}
            disabled={logoutMutation.isPending}
          >
            Sair
          </button>
        </div>
      </header>
      <main className="app-main">
        <Outlet />
      </main>
    </div>
  )
}
