import { Navigate } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import type { Role } from '../../types'

interface RoleRouteProps {
  roles: Role[]
  children: React.ReactNode
}

export function RoleRoute({ roles, children }: RoleRouteProps) {
  const { user } = useAuth()

  if (!user || !roles.includes(user.role)) {
    return <Navigate to="/dashboard" replace />
  }

  return <>{children}</>
}
