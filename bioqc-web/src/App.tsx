import { lazy, Suspense } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { AppLayout } from './components/layout/AppLayout'
import { PrivateRoute } from './components/layout/PrivateRoute'
import { RoleRoute } from './components/layout/RoleRoute'

const DashboardPage = lazy(() => import('./pages/DashboardPage').then((module) => ({ default: module.DashboardPage })))
const LoginPage = lazy(() => import('./pages/LoginPage').then((module) => ({ default: module.LoginPage })))
const ProinPage = lazy(() => import('./pages/ProinPage').then((module) => ({ default: module.ProinPage })))
const ReagentesPage = lazy(() =>
  import('./pages/ReagentesPage').then((module) => ({ default: module.ReagentesPage })),
)
const ManutencaoPage = lazy(() =>
  import('./pages/ManutencaoPage').then((module) => ({ default: module.ManutencaoPage })),
)
const RelatoriosPage = lazy(() =>
  import('./pages/RelatoriosPage').then((module) => ({ default: module.RelatoriosPage })),
)
const ReportBuilder = lazy(() =>
  import('./components/relatoriosV2/ReportBuilder').then((module) => ({ default: module.ReportBuilder })),
)
const VerifyReportPage = lazy(() =>
  import('./pages/VerifyReportPage').then((module) => ({ default: module.VerifyReportPage })),
)
const AdminPage = lazy(() => import('./pages/AdminPage').then((module) => ({ default: module.AdminPage })))
const ConfiguracaoPage = lazy(() =>
  import('./pages/ConfiguracaoPage').then((module) => ({ default: module.ConfiguracaoPage })),
)
const ResetPasswordPage = lazy(() =>
  import('./pages/ResetPasswordPage').then((module) => ({ default: module.ResetPasswordPage })),
)

function RouteFallback() {
  return (
    <div className="mx-auto flex min-h-[40vh] max-w-7xl items-center justify-center px-4 py-8 sm:px-6 lg:px-8">
      <div className="w-full max-w-3xl rounded-3xl border border-neutral-200 bg-white p-6 shadow-sm">
        <div className="h-5 w-40 animate-pulse rounded-full bg-neutral-200" />
        <div className="mt-6 space-y-3">
          <div className="h-4 animate-pulse rounded-full bg-neutral-100" />
          <div className="h-4 w-5/6 animate-pulse rounded-full bg-neutral-100" />
          <div className="h-32 animate-pulse rounded-3xl bg-neutral-100" />
        </div>
      </div>
    </div>
  )
}

export default function App() {
  return (
    <Routes>
      <Route
        path="/login"
        element={
          <Suspense fallback={<RouteFallback />}>
            <LoginPage />
          </Suspense>
        }
      />
      <Route
        path="/reset-password"
        element={
          <Suspense fallback={<RouteFallback />}>
            <ResetPasswordPage />
          </Suspense>
        }
      />
      <Route
        path="/r/verify/:hash"
        element={
          <Suspense fallback={<RouteFallback />}>
            <VerifyReportPage />
          </Suspense>
        }
      />
      <Route element={<PrivateRoute />}>
        <Route element={<AppLayout />}>
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route
            path="/dashboard"
            element={
              <Suspense fallback={<RouteFallback />}>
                <DashboardPage />
              </Suspense>
            }
          />
          <Route
            path="/qc"
            element={
              <Suspense fallback={<RouteFallback />}>
                <ProinPage />
              </Suspense>
            }
          />
          <Route
            path="/reagentes"
            element={
              <Suspense fallback={<RouteFallback />}>
                <ReagentesPage />
              </Suspense>
            }
          />
          <Route
            path="/manutencao"
            element={
              <Suspense fallback={<RouteFallback />}>
                <ManutencaoPage />
              </Suspense>
            }
          />
          <Route
            path="/relatorios"
            element={
              <Suspense fallback={<RouteFallback />}>
                <RelatoriosPage />
              </Suspense>
            }
          />
          <Route
            path="/relatorios/legado"
            element={
              <Suspense fallback={<RouteFallback />}>
                <RelatoriosPage />
              </Suspense>
            }
          />
          <Route
            path="/relatorios/:code"
            element={
              <Suspense fallback={<RouteFallback />}>
                <ReportBuilder />
              </Suspense>
            }
          />
          <Route
            path="/admin"
            element={
              <Suspense fallback={<RouteFallback />}>
                <RoleRoute roles={['ADMIN']}>
                  <AdminPage />
                </RoleRoute>
              </Suspense>
            }
          />
          <Route
            path="/config"
            element={
              <Suspense fallback={<RouteFallback />}>
                <RoleRoute roles={['ADMIN']}>
                  <ConfiguracaoPage />
                </RoleRoute>
              </Suspense>
            }
          />
        </Route>
      </Route>
    </Routes>
  )
}
