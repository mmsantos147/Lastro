import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { useCurrentUser } from './useCurrentUser'

/** Renders its children only for a logged-in user; otherwise redirects to the login page. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { data: user, isPending, isError } = useCurrentUser()
  const location = useLocation()

  if (isPending) return <p className="status">Carregando…</p>
  if (isError) return <p role="alert" className="status">Não foi possível contatar o servidor.</p>
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return children
}
