import { createBrowserRouter, Navigate, type RouteObject } from 'react-router'
import { RequireAuth } from './auth/RequireAuth'
import { Layout } from './components/Layout'
import { LoginPage } from './pages/LoginPage'
import { OverviewPage } from './pages/OverviewPage'
import { OperationsPage } from './pages/operations/OperationsPage'
import { PositionsPage } from './pages/PositionsPage'
import { AllocationPage } from './pages/AllocationPage'
import { IncomePage } from './pages/IncomePage'
import { ContributionPage } from './pages/ContributionPage'
import { StrategyPage } from './pages/strategy/StrategyPage'
import { AnalysesPage } from './pages/analyses/AnalysesPage'
import { AnalysisDetailPage } from './pages/analyses/AnalysisDetailPage'

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  {
    path: '/',
    element: (
      <RequireAuth>
        <Layout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <OverviewPage /> },
      { path: 'alocacao', element: <AllocationPage /> },
      { path: 'proventos', element: <IncomePage /> },
      { path: 'posicoes', element: <PositionsPage /> },
      { path: 'operacoes', element: <OperationsPage /> },
      { path: 'aporte', element: <ContributionPage /> },
      { path: 'estrategia', element: <StrategyPage /> },
      { path: 'analises', element: <AnalysesPage /> },
      { path: 'analises/:id', element: <AnalysisDetailPage /> },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
]

export const router = createBrowserRouter(routes)
