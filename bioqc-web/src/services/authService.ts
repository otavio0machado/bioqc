import { api, persistAuth } from './api'
import type {
  AuthResponse,
  ForgotPasswordRequest,
  LoginRequest,
  PasswordResetResponse,
  RefreshTokenRequest,
  RegisterRequest,
  ResetPasswordRequest,
  User,
} from '../types'

export const authService = {
  async login(request: LoginRequest) {
    const response = await api.post<AuthResponse>('/auth/login', request)
    persistAuth(response.data)
    return response.data
  },
  async refreshToken(request: RefreshTokenRequest) {
    const payload = request.refreshToken ? request : undefined
    const response = await api.post<AuthResponse>('/auth/refresh', payload)
    persistAuth(response.data)
    return response.data
  },
  async logout() {
    await api.post('/auth/logout')
  },
  async register(request: RegisterRequest) {
    const response = await api.post<User>('/auth/register', request)
    return response.data
  },
  async requestPasswordReset(request: ForgotPasswordRequest) {
    const response = await api.post<PasswordResetResponse>('/auth/forgot-password', request)
    return response.data
  },
  async resetPassword(request: ResetPasswordRequest) {
    const response = await api.post<PasswordResetResponse>('/auth/reset-password', request)
    return response.data
  },
}
