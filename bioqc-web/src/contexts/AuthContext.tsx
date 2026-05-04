import {
  startTransition,
  useEffect,
  useState,
  type PropsWithChildren,
} from 'react'
import { useNavigate } from 'react-router-dom'
import { clearAuth, getAuthSnapshot } from '../services/api'
import { authService } from '../services/authService'
import type { LoginRequest, User } from '../types'
import { AuthContext, type AuthContextValue } from './auth-context'

export function AuthProvider({ children }: PropsWithChildren) {
  const navigate = useNavigate()
  const [user, setUser] = useState<User | null>(() => getAuthSnapshot().user)
  const [isLoading, setIsLoading] = useState(true)

  const restoreSession = async () => {
    setIsLoading(true)

    try {
      const auth = await authService.refreshToken({ refreshToken: '' })
      startTransition(() => {
        setUser(auth.user)
      })
    } catch {
      clearAuth()
      startTransition(() => {
        setUser(null)
      })
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    void restoreSession()
  }, [])

  const login = async (username: string, password: string) => {
    setIsLoading(true)

    try {
      const auth = await authService.login({ username, password } satisfies LoginRequest)
      startTransition(() => {
        setUser(auth.user)
      })
      navigate('/dashboard')
    } finally {
      setIsLoading(false)
    }
  }

  const logout = async () => {
    try {
      await authService.logout()
    } catch {
      // A limpeza local precisa acontecer mesmo se a chamada falhar.
    } finally {
      clearAuth()
    }
    startTransition(() => {
      setUser(null)
    })
    navigate('/login')
  }

  const refreshToken = async () => {
    const auth = await authService.refreshToken({ refreshToken: '' })
    startTransition(() => {
      setUser(auth.user)
    })
  }

  const value: AuthContextValue = {
    user,
    isAuthenticated: Boolean(user),
    isLoading,
    login,
    logout,
    refreshToken,
    restoreSession,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
