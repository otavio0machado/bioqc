import type { AdminResetPasswordRequest, AdminUpdateUserRequest, AdminUserRequest, User } from '../types'
import { api } from './api'

export const adminService = {
  async getUsers(): Promise<User[]> {
    const { data } = await api.get<User[]>('/admin/users')
    return data
  },

  async createUser(request: AdminUserRequest): Promise<User> {
    const { data } = await api.post<User>('/admin/users', request)
    return data
  },

  async updateUser(id: string, request: AdminUpdateUserRequest): Promise<User> {
    const { data } = await api.put<User>(`/admin/users/${id}`, request)
    return data
  },

  async resetPassword(id: string, request: AdminResetPasswordRequest): Promise<void> {
    await api.put(`/admin/users/${id}/password`, request)
  },
}
