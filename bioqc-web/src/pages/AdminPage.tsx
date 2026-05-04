import {
  Activity,
  Check,
  Clock,
  Eye,
  EyeOff,
  KeyRound,
  Pencil,
  Search,
  Shield,
  ShieldCheck,
  UserCheck,
  UserPlus,
  UserX,
  Users,
} from 'lucide-react'
import { useMemo, useState } from 'react'
import { Button, Card, EmptyState, Input, Modal, Select, StatCard, useToast } from '../components/ui'
import { useAuditLogs, useCreateUser, useResetPassword, useUpdateUser, useUsers } from '../hooks/useAdmin'
import { ALL_PERMISSIONS, PERMISSION_LABELS, ROLE_LABELS } from '../lib/permissions'
import type { User } from '../types'

const ROLES = ['ADMIN', 'FUNCIONARIO', 'VIGILANCIA_SANITARIA', 'VISUALIZADOR'] as const

const ROLE_COLORS: Record<string, string> = {
  ADMIN: 'bg-violet-100 text-violet-800',
  FUNCIONARIO: 'bg-sky-100 text-sky-800',
  VIGILANCIA_SANITARIA: 'bg-amber-100 text-amber-800',
  VISUALIZADOR: 'bg-neutral-100 text-neutral-600',
}

const ROLE_ICONS: Record<string, typeof Shield> = {
  ADMIN: ShieldCheck,
  FUNCIONARIO: UserCheck,
  VIGILANCIA_SANITARIA: Shield,
  VISUALIZADOR: Eye,
}

export function AdminPage() {
  const { data: users = [], isLoading } = useUsers()
  const [createOpen, setCreateOpen] = useState(false)
  const [editUser, setEditUser] = useState<User | null>(null)
  const [resetUser, setResetUser] = useState<User | null>(null)
  const [searchQuery, setSearchQuery] = useState('')
  const [filterRole, setFilterRole] = useState<string>('')

  const filteredUsers = useMemo(() => {
    return users.filter((u) => {
      const matchesSearch =
        !searchQuery ||
        u.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
        u.username.toLowerCase().includes(searchQuery.toLowerCase())
      const matchesRole = !filterRole || u.role === filterRole
      return matchesSearch && matchesRole
    })
  }, [users, searchQuery, filterRole])

  const stats = useMemo(() => {
    const active = users.filter((u) => u.isActive).length
    const inactive = users.filter((u) => !u.isActive).length
    const admins = users.filter((u) => u.role === 'ADMIN').length
    const funcionarios = users.filter((u) => u.role === 'FUNCIONARIO').length
    return { total: users.length, active, inactive, admins, funcionarios }
  }, [users])

  return (
    <div className="mx-auto max-w-7xl space-y-6 px-4 py-8 sm:px-6 lg:px-8">
      {/* Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-neutral-900">Gestão de Usuários</h1>
          <p className="mt-1 text-sm text-neutral-500">
            Gerencie acessos, perfis e permissões do sistema
          </p>
        </div>
        <Button onClick={() => setCreateOpen(true)} size="lg">
          <UserPlus className="mr-2 h-4 w-4" />
          Novo Usuário
        </Button>
      </div>

      {/* KPI Cards */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="Total de Usuários" value={stats.total} icon={<Users className="h-5 w-5" />} iconColor="bg-green-800" />
        <StatCard label="Ativos" value={stats.active} icon={<UserCheck className="h-5 w-5" />} iconColor="bg-emerald-600" />
        <StatCard label="Administradores" value={stats.admins} icon={<ShieldCheck className="h-5 w-5" />} iconColor="bg-violet-600" />
        <StatCard label="Funcionários" value={stats.funcionarios} icon={<UserCheck className="h-5 w-5" />} iconColor="bg-sky-600" />
      </div>

      {/* Search and Filter */}
      <Card className="flex flex-col gap-4 sm:flex-row sm:items-end">
        <div className="flex-1">
          <Input
            label="Buscar"
            placeholder="Nome ou usuário..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            icon={<Search className="h-4 w-4" />}
          />
        </div>
        <div className="w-full sm:w-56">
          <Select
            label="Filtrar perfil"
            value={filterRole}
            onChange={(e) => setFilterRole(e.target.value)}
          >
            <option value="">Todos os perfis</option>
            {ROLES.map((r) => (
              <option key={r} value={r}>{ROLE_LABELS[r]}</option>
            ))}
          </Select>
        </div>
      </Card>

      {/* User Cards Grid */}
      {isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="h-52 animate-pulse rounded-3xl bg-neutral-100" />
          ))}
        </div>
      ) : filteredUsers.length === 0 ? (
        <EmptyState
          icon={<Users className="h-8 w-8" />}
          title={searchQuery || filterRole ? 'Nenhum usuário encontrado' : 'Nenhum usuário cadastrado'}
          description={
            searchQuery || filterRole
              ? 'Tente ajustar os filtros de busca.'
              : 'Clique em "Novo Usuário" para cadastrar o primeiro acesso.'
          }
          action={
            !searchQuery && !filterRole
              ? { label: 'Novo Usuário', onClick: () => setCreateOpen(true) }
              : undefined
          }
        />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {filteredUsers.map((u) => (
            <UserCard
              key={u.id}
              user={u}
              onEdit={() => setEditUser(u)}
              onResetPassword={() => setResetUser(u)}
            />
          ))}
        </div>
      )}

      {/* Activity Log */}
      <ActivityLogSection users={users} />

      <CreateUserModal isOpen={createOpen} onClose={() => setCreateOpen(false)} />
      {editUser ? <EditUserModal user={editUser} onClose={() => setEditUser(null)} /> : null}
      {resetUser ? <ResetPasswordModal user={resetUser} onClose={() => setResetUser(null)} /> : null}
    </div>
  )
}

/* ─── User Card ─── */

function UserCard({
  user,
  onEdit,
  onResetPassword,
}: {
  user: User
  onEdit: () => void
  onResetPassword: () => void
}) {
  const RoleIcon = ROLE_ICONS[user.role] ?? Shield
  const initials = user.name
    .split(' ')
    .map((p) => p[0])
    .join('')
    .slice(0, 2)
    .toUpperCase()

  return (
    <Card className="flex flex-col justify-between space-y-4 transition hover:shadow-elevated">
      <div className="flex items-start gap-4">
        {/* Avatar */}
        <div
          className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-full text-sm font-bold ${
            user.isActive ? 'bg-green-800 text-white' : 'bg-neutral-300 text-neutral-600'
          }`}
        >
          {initials}
        </div>

        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <h3 className="truncate text-sm font-semibold text-neutral-900">{user.name}</h3>
            {!user.isActive && (
              <span className="shrink-0 rounded-full bg-red-50 px-2 py-0.5 text-[10px] font-medium text-red-600">
                Inativo
              </span>
            )}
          </div>
          <p className="text-sm text-neutral-500">@{user.username}</p>
        </div>
      </div>

      {/* Role Badge */}
      <div className="flex items-center gap-2">
        <span className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold ${ROLE_COLORS[user.role] ?? 'bg-neutral-100 text-neutral-600'}`}>
          <RoleIcon className="h-3.5 w-3.5" />
          {ROLE_LABELS[user.role] ?? user.role}
        </span>
      </div>

      {/* Permissions */}
      {user.role === 'FUNCIONARIO' ? (
        <div className="space-y-1.5">
          <p className="text-[11px] font-medium uppercase tracking-wider text-neutral-400">Permissões</p>
          {user.permissions.length > 0 ? (
            <div className="flex flex-wrap gap-1.5">
              {user.permissions.map((p) => (
                <span
                  key={p}
                  className="inline-flex items-center gap-1 rounded-lg bg-green-50 px-2 py-0.5 text-[11px] font-medium text-green-700"
                >
                  <Check className="h-3 w-3" />
                  {PERMISSION_LABELS[p] ?? p}
                </span>
              ))}
            </div>
          ) : (
            <p className="text-xs text-neutral-400">Nenhuma permissão atribuída</p>
          )}
        </div>
      ) : user.role === 'ADMIN' ? (
        <div className="rounded-xl bg-violet-50 px-3 py-2 text-xs text-violet-700">
          Acesso total ao sistema
        </div>
      ) : user.role === 'VIGILANCIA_SANITARIA' ? (
        <div className="rounded-xl bg-amber-50 px-3 py-2 text-xs text-amber-700">
          Visualização e download completos
        </div>
      ) : (
        <div className="rounded-xl bg-neutral-50 px-3 py-2 text-xs text-neutral-500">
          Apenas visualização
        </div>
      )}

      {/* Actions */}
      <div className="flex gap-2 border-t border-neutral-100 pt-3">
        <Button variant="secondary" size="sm" className="flex-1" onClick={onEdit}>
          <Pencil className="mr-1.5 h-3.5 w-3.5" />
          Editar
        </Button>
        <Button variant="ghost" size="sm" className="flex-1" onClick={onResetPassword}>
          <KeyRound className="mr-1.5 h-3.5 w-3.5" />
          Senha
        </Button>
      </div>
    </Card>
  )
}

/* ─── Create User Modal ─── */

function CreateUserModal({ isOpen, onClose }: { isOpen: boolean; onClose: () => void }) {
  const { toast } = useToast()
  const createUser = useCreateUser()
  const [username, setUsername] = useState('')
  const [name, setName] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState('FUNCIONARIO')
  const [email, setEmail] = useState('')
  const [permissions, setPermissions] = useState<string[]>([])
  const [showPw, setShowPw] = useState(false)

  const handleSubmit = async () => {
    if (!username.trim() || !name.trim() || !password) {
      toast.warning('Preencha o nome de usuário, nome completo e senha.')
      return
    }
    try {
      await createUser.mutateAsync({
        username: username.trim().toLowerCase(),
        name: name.trim(),
        password,
        role,
        email: email.trim() || undefined,
        permissions: role === 'FUNCIONARIO' ? permissions : undefined,
      })
      toast.success(`Usuário "${name.trim()}" criado com sucesso!`)
      resetForm()
      onClose()
    } catch {
      toast.error('Erro ao criar usuário. Verifique se o nome de usuário já existe.')
    }
  }

  const resetForm = () => {
    setUsername('')
    setName('')
    setPassword('')
    setRole('FUNCIONARIO')
    setEmail('')
    setPermissions([])
    setShowPw(false)
  }

  return (
    <Modal
      isOpen={isOpen}
      onClose={() => { resetForm(); onClose() }}
      title="Novo Usuário"
      footer={
        <div className="flex justify-end gap-3">
          <Button variant="ghost" onClick={() => { resetForm(); onClose() }}>Cancelar</Button>
          <Button onClick={() => void handleSubmit()} loading={createUser.isPending}>
            <UserPlus className="mr-2 h-4 w-4" />
            Criar Usuário
          </Button>
        </div>
      }
    >
      <div className="space-y-5">
        <div className="grid gap-4 md:grid-cols-2">
          <Input
            label="Nome de usuário (login)"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="Ex: joao.silva"
          />
          <Input
            label="Nome completo"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Ex: João da Silva"
          />
        </div>

        <div className="relative">
          <Input
            label="Senha"
            type={showPw ? 'text' : 'password'}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="Mínimo 4 caracteres"
          />
          <button
            type="button"
            className="absolute right-3 top-[2.65rem] rounded-full p-1 text-neutral-400 transition hover:text-neutral-700"
            onClick={() => setShowPw(!showPw)}
          >
            {showPw ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
          </button>
        </div>

        <Input
          label="Email (opcional)"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="email@exemplo.com"
        />

        {/* Role Selection with visual cards */}
        <div>
          <label className="mb-2 block text-sm font-medium text-neutral-700">Perfil de acesso</label>
          <div className="grid gap-2 sm:grid-cols-2">
            {ROLES.map((r) => {
              const Icon = ROLE_ICONS[r] ?? Shield
              const selected = role === r
              return (
                <button
                  key={r}
                  type="button"
                  onClick={() => setRole(r)}
                  className={`flex items-center gap-3 rounded-xl border-2 px-4 py-3 text-left transition ${
                    selected
                      ? 'border-green-600 bg-green-50'
                      : 'border-neutral-200 bg-white hover:border-neutral-300'
                  }`}
                >
                  <Icon className={`h-5 w-5 ${selected ? 'text-green-700' : 'text-neutral-400'}`} />
                  <div>
                    <div className={`text-sm font-semibold ${selected ? 'text-green-800' : 'text-neutral-700'}`}>
                      {ROLE_LABELS[r]}
                    </div>
                    <div className="text-[11px] text-neutral-500">{ROLE_DESCRIPTIONS[r]}</div>
                  </div>
                </button>
              )
            })}
          </div>
        </div>

        {/* Permissions for Funcionario */}
        {role === 'FUNCIONARIO' ? (
          <div className="rounded-2xl border border-sky-200 bg-sky-50 p-4">
            <div className="mb-3 flex items-center justify-between">
              <label className="text-sm font-semibold text-sky-900">Permissões do Funcionário</label>
              <button
                type="button"
                className="text-xs font-medium text-sky-700 hover:text-sky-900"
                onClick={() =>
                  setPermissions(
                    permissions.length === ALL_PERMISSIONS.length ? [] : [...ALL_PERMISSIONS],
                  )
                }
              >
                {permissions.length === ALL_PERMISSIONS.length ? 'Desmarcar tudo' : 'Marcar tudo'}
              </button>
            </div>
            <div className="grid gap-2 sm:grid-cols-2">
              {ALL_PERMISSIONS.map((p) => {
                const checked = permissions.includes(p)
                return (
                  <label
                    key={p}
                    className={`flex cursor-pointer items-center gap-2.5 rounded-xl border px-3 py-2.5 transition ${
                      checked
                        ? 'border-sky-300 bg-white'
                        : 'border-transparent bg-sky-50 hover:bg-white'
                    }`}
                  >
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={(e) =>
                        setPermissions(
                          e.target.checked
                            ? [...permissions, p]
                            : permissions.filter((x) => x !== p),
                        )
                      }
                      className="h-4 w-4 rounded border-sky-300 text-sky-600 focus:ring-sky-500"
                    />
                    <span className="text-sm text-sky-900">{PERMISSION_LABELS[p]}</span>
                  </label>
                )
              })}
            </div>
          </div>
        ) : null}
      </div>
    </Modal>
  )
}

/* ─── Edit User Modal ─── */

function EditUserModal({ user, onClose }: { user: User; onClose: () => void }) {
  const { toast } = useToast()
  const updateUser = useUpdateUser()
  const [name, setName] = useState(user.name)
  const [role, setRole] = useState<string>(user.role)
  const [email, setEmail] = useState(user.email ?? '')
  const [isActive, setIsActive] = useState(user.isActive)
  const [permissions, setPermissions] = useState<string[]>(user.permissions ?? [])

  const handleSubmit = async () => {
    try {
      await updateUser.mutateAsync({
        id: user.id,
        request: {
          name: name.trim(),
          role,
          isActive,
          email: email.trim() || undefined,
          permissions: role === 'FUNCIONARIO' ? permissions : undefined,
        },
      })
      toast.success('Usuário atualizado com sucesso!')
      onClose()
    } catch {
      toast.error('Erro ao atualizar usuário.')
    }
  }

  return (
    <Modal
      isOpen
      onClose={onClose}
      title={`Editar — ${user.name}`}
      footer={
        <div className="flex justify-end gap-3">
          <Button variant="ghost" onClick={onClose}>Cancelar</Button>
          <Button onClick={() => void handleSubmit()} loading={updateUser.isPending}>
            Salvar Alterações
          </Button>
        </div>
      }
    >
      <div className="space-y-5">
        <div className="flex items-center gap-4 rounded-2xl bg-neutral-50 p-4">
          <div className="flex h-12 w-12 items-center justify-center rounded-full bg-green-800 text-sm font-bold text-white">
            {user.name.split(' ').map((p) => p[0]).join('').slice(0, 2).toUpperCase()}
          </div>
          <div>
            <div className="font-semibold text-neutral-900">{user.name}</div>
            <div className="text-sm text-neutral-500">@{user.username}</div>
          </div>
        </div>

        <div className="grid gap-4 md:grid-cols-2">
          <Input
            label="Nome completo"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          <Input
            label="Email (opcional)"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </div>

        <Select label="Perfil de acesso" value={role} onChange={(e) => setRole(e.target.value)}>
          {ROLES.map((r) => (
            <option key={r} value={r}>{ROLE_LABELS[r]}</option>
          ))}
        </Select>

        {role === 'FUNCIONARIO' ? (
          <div className="rounded-2xl border border-sky-200 bg-sky-50 p-4">
            <div className="mb-3 flex items-center justify-between">
              <label className="text-sm font-semibold text-sky-900">Permissões</label>
              <button
                type="button"
                className="text-xs font-medium text-sky-700 hover:text-sky-900"
                onClick={() =>
                  setPermissions(
                    permissions.length === ALL_PERMISSIONS.length ? [] : [...ALL_PERMISSIONS],
                  )
                }
              >
                {permissions.length === ALL_PERMISSIONS.length ? 'Desmarcar tudo' : 'Marcar tudo'}
              </button>
            </div>
            <div className="grid gap-2 sm:grid-cols-2">
              {ALL_PERMISSIONS.map((p) => {
                const checked = permissions.includes(p)
                return (
                  <label
                    key={p}
                    className={`flex cursor-pointer items-center gap-2.5 rounded-xl border px-3 py-2.5 transition ${
                      checked
                        ? 'border-sky-300 bg-white'
                        : 'border-transparent bg-sky-50 hover:bg-white'
                    }`}
                  >
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={(e) =>
                        setPermissions(
                          e.target.checked
                            ? [...permissions, p]
                            : permissions.filter((x) => x !== p),
                        )
                      }
                      className="h-4 w-4 rounded border-sky-300 text-sky-600 focus:ring-sky-500"
                    />
                    <span className="text-sm text-sky-900">{PERMISSION_LABELS[p]}</span>
                  </label>
                )
              })}
            </div>
          </div>
        ) : null}

        {/* Status toggle */}
        <div className="flex items-center justify-between rounded-2xl border border-neutral-200 px-4 py-3">
          <div>
            <div className="text-sm font-medium text-neutral-900">Status do usuário</div>
            <div className="text-xs text-neutral-500">
              {isActive ? 'O usuário pode acessar o sistema' : 'O acesso está bloqueado'}
            </div>
          </div>
          <button
            type="button"
            role="switch"
            aria-checked={isActive}
            onClick={() => setIsActive(!isActive)}
            className={`relative inline-flex h-7 w-12 shrink-0 cursor-pointer rounded-full transition-colors ${
              isActive ? 'bg-green-600' : 'bg-neutral-300'
            }`}
          >
            <span
              className={`pointer-events-none inline-block h-5 w-5 translate-y-1 rounded-full bg-white shadow-sm transition-transform ${
                isActive ? 'translate-x-6' : 'translate-x-1'
              }`}
            />
          </button>
        </div>
      </div>
    </Modal>
  )
}

/* ─── Reset Password Modal ─── */

function ResetPasswordModal({ user, onClose }: { user: User; onClose: () => void }) {
  const { toast } = useToast()
  const resetPassword = useResetPassword()
  const [newPassword, setNewPassword] = useState('')
  const [showPw, setShowPw] = useState(false)

  const handleSubmit = async () => {
    if (newPassword.length < 4) {
      toast.warning('A senha deve ter pelo menos 4 caracteres.')
      return
    }
    try {
      await resetPassword.mutateAsync({ id: user.id, request: { newPassword } })
      toast.success(`Senha de ${user.name} redefinida com sucesso!`)
      setNewPassword('')
      onClose()
    } catch {
      toast.error('Erro ao redefinir senha.')
    }
  }

  return (
    <Modal
      isOpen
      onClose={() => { setNewPassword(''); onClose() }}
      title="Redefinir Senha"
      footer={
        <div className="flex justify-end gap-3">
          <Button variant="ghost" onClick={() => { setNewPassword(''); onClose() }}>Cancelar</Button>
          <Button onClick={() => void handleSubmit()} loading={resetPassword.isPending}>
            <KeyRound className="mr-2 h-4 w-4" />
            Redefinir Senha
          </Button>
        </div>
      }
    >
      <div className="space-y-4">
        <div className="flex items-center gap-3 rounded-2xl bg-amber-50 px-4 py-3 text-sm text-amber-800">
          <UserX className="h-5 w-5 shrink-0" />
          <span>
            Você está redefinindo a senha de <strong>{user.name}</strong> (@{user.username}).
            A sessão ativa será mantida até expirar.
          </span>
        </div>

        <div className="relative">
          <Input
            label="Nova senha"
            type={showPw ? 'text' : 'password'}
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            placeholder="Mínimo 4 caracteres"
          />
          <button
            type="button"
            className="absolute right-3 top-[2.65rem] rounded-full p-1 text-neutral-400 transition hover:text-neutral-700"
            onClick={() => setShowPw(!showPw)}
          >
            {showPw ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
          </button>
        </div>
      </div>
    </Modal>
  )
}

/* ─── Activity Log Section ─── */

const ACTION_LABELS: Record<string, string> = {
  LOGIN: 'Fez login no sistema',
  CRIAR_REGISTRO_CQ: 'Registrou medição CQ',
  CRIAR_USUARIO: 'Criou novo usuário',
  EDITAR_USUARIO: 'Editou perfil de usuário',
  RESETAR_SENHA: 'Redefiniu senha de usuário',
}

const ACTION_COLORS: Record<string, string> = {
  LOGIN: 'bg-blue-100 text-blue-700',
  CRIAR_REGISTRO_CQ: 'bg-green-100 text-green-700',
  CRIAR_USUARIO: 'bg-violet-100 text-violet-700',
  EDITAR_USUARIO: 'bg-amber-100 text-amber-700',
  RESETAR_SENHA: 'bg-red-100 text-red-700',
}

function formatLogDate(iso: string) {
  try {
    const date = new Date(iso)
    return date.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric' }) +
      ' ' + date.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
  } catch {
    return iso
  }
}

function ActivityLogSection({ users }: { users: User[] }) {
  const [filterUser, setFilterUser] = useState<string>('')
  const { data: logs = [], isLoading } = useAuditLogs(filterUser || undefined)

  return (
    <Card>
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-3">
          <div className="rounded-full bg-green-100 p-2.5 text-green-700">
            <Activity className="h-5 w-5" />
          </div>
          <div>
            <h3 className="text-lg font-semibold text-neutral-900">Atividade Recente</h3>
            <p className="text-sm text-neutral-500">Movimentações e ações dos usuários no sistema</p>
          </div>
        </div>
        <div className="w-full sm:w-56">
          <Select
            label="Filtrar por usuário"
            value={filterUser}
            onChange={(e) => setFilterUser(e.target.value)}
          >
            <option value="">Todos os usuários</option>
            {users.map((u) => (
              <option key={u.id} value={u.id}>{u.name}</option>
            ))}
          </Select>
        </div>
      </div>

      {isLoading ? (
        <div className="mt-4 space-y-3">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="h-14 animate-pulse rounded-xl bg-neutral-100" />
          ))}
        </div>
      ) : logs.length === 0 ? (
        <div className="mt-4 rounded-2xl border border-dashed border-neutral-200 bg-neutral-50 px-6 py-10 text-center text-sm text-neutral-500">
          Nenhuma atividade registrada{filterUser ? ' para este usuário' : ''}.
        </div>
      ) : (
        <div className="mt-4 space-y-1">
          {logs.map((log) => {
            const details = log.details as Record<string, unknown> | null
            return (
              <div
                key={log.id}
                className="flex items-start gap-3 rounded-xl px-3 py-2.5 transition hover:bg-neutral-50"
              >
                <div className={`mt-0.5 shrink-0 rounded-lg p-1.5 ${ACTION_COLORS[log.action] ?? 'bg-neutral-100 text-neutral-600'}`}>
                  <Clock className="h-3.5 w-3.5" />
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-x-2 gap-y-0.5">
                    <span className="text-sm font-medium text-neutral-900">
                      {log.userName ?? log.username ?? 'Sistema'}
                    </span>
                    <span className="text-sm text-neutral-600">
                      {ACTION_LABELS[log.action] ?? log.action}
                    </span>
                  </div>
                  {details ? (
                    <div className="mt-0.5 flex flex-wrap gap-1.5">
                      {Object.entries(details).map(([key, val]) =>
                        val != null ? (
                          <span key={key} className="inline-flex rounded bg-neutral-100 px-1.5 py-0.5 text-[11px] text-neutral-600">
                            {key}: {String(val)}
                          </span>
                        ) : null,
                      )}
                    </div>
                  ) : null}
                </div>
                <span className="shrink-0 whitespace-nowrap text-xs text-neutral-400">
                  {formatLogDate(log.createdAt)}
                </span>
              </div>
            )
          })}
        </div>
      )}
    </Card>
  )
}

/* ─── Constants ─── */

const ROLE_DESCRIPTIONS: Record<string, string> = {
  ADMIN: 'Controle total do sistema',
  FUNCIONARIO: 'Permissões personalizadas',
  VIGILANCIA_SANITARIA: 'Visualização e download',
  VISUALIZADOR: 'Apenas visualização',
}
