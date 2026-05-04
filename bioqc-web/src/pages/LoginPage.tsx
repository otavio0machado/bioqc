import { zodResolver } from '@hookform/resolvers/zod'
import { Eye, EyeOff, Lock, Shield, UserIcon } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Navigate } from 'react-router-dom'
import { Button, Card, Input, useToast } from '../components/ui'
import { useAuth } from '../hooks/useAuth'
import { type LoginFormValues, loginSchema } from '../lib/authSchemas'
import logoBio from '../assets/logobio.png'

export function LoginPage() {
  const { isAuthenticated, login } = useAuth()
  const { toast } = useToast()
  const [showPassword, setShowPassword] = useState(false)
  const loginForm = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      username: '',
      password: '',
    },
    mode: 'onChange',
  })

  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />
  }

  const handleLogin = loginForm.handleSubmit(async (data) => {
    try {
      await login(data.username, data.password)
    } catch {
      toast.error('Credenciais inválidas. Confira seu nome de usuário e senha.')
    }
  })

  return (
    <div className="min-h-screen bg-neutral-50">
      <div className="grid min-h-screen lg:grid-cols-[1.2fr_0.8fr]">
        <section className="relative hidden overflow-hidden bg-gradient-to-br from-green-900 via-green-800 to-green-700 px-10 py-12 text-white lg:flex">
          <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_right,_rgba(255,255,255,0.18),_transparent_30%)]" />
          <div className="relative flex h-full w-full items-center">
            <div className="max-w-2xl space-y-8">
              <div className="inline-flex items-center gap-3 rounded-full border border-white/20 bg-white/10 px-4 py-2 text-sm font-medium backdrop-blur-md">
                <img src={logoBio} alt="Biodiagnóstico" className="h-6 w-6 rounded-full bg-white/90 p-0.5" />
                Biodiagnóstico 4.0
              </div>
              <div className="space-y-5">
                <h1 className="max-w-xl text-5xl font-bold leading-tight">
                  Controle de qualidade laboratorial com confiança operacional.
                </h1>
                <p className="max-w-lg text-lg text-green-50/85">
                  Plataforma unificada para monitorar CQ, reagentes, manutenção e análises assistidas.
                </p>
              </div>
              <Card glass className="max-w-xl text-white">
                <p className="text-sm leading-6 text-green-50/90">
                  Mantenha os indicadores críticos visíveis, aplique regras de Westgard em tempo real e
                  acompanhe tendências antes que virem problema.
                </p>
              </Card>
            </div>
          </div>
        </section>

        <section className="flex items-center justify-center px-6 py-12 sm:px-10">
          <div className="w-full max-w-md space-y-8">
            <div className="space-y-3">
              <img src={logoBio} alt="Biodiagnóstico" className="h-16 w-16 rounded-2xl shadow-sm" />
              <div className="text-sm font-semibold uppercase tracking-[0.18em] text-green-800">Biodiagnóstico</div>
              <h2 className="text-3xl font-bold text-neutral-900">Acesse sua conta</h2>
              <p className="text-neutral-500">Sistema de Controle de Qualidade</p>
            </div>

            <form className="space-y-5" onSubmit={handleLogin}>
              <Input
                label="Nome de usuário"
                type="text"
                placeholder="seu.usuario"
                icon={<UserIcon className="h-4 w-4" />}
                error={loginForm.formState.errors.username?.message}
                autoComplete="username"
                {...loginForm.register('username')}
              />

              <div className="relative">
                <Input
                  label="Senha"
                  type={showPassword ? 'text' : 'password'}
                  placeholder="Digite sua senha"
                  icon={<Lock className="h-4 w-4" />}
                  error={loginForm.formState.errors.password?.message}
                  autoComplete="current-password"
                  {...loginForm.register('password')}
                />
                <button
                  type="button"
                  className="absolute right-3 top-[2.65rem] rounded-full p-1 text-neutral-400 transition hover:text-neutral-700"
                  onClick={() => setShowPassword((value) => !value)}
                  aria-label={showPassword ? 'Ocultar senha' : 'Mostrar senha'}
                >
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>

              <Button type="submit" size="xl" className="w-full" loading={loginForm.formState.isSubmitting}>
                Entrar
              </Button>
            </form>

            <div className="flex items-center gap-3 rounded-2xl bg-green-50 px-4 py-3 text-sm text-green-900">
              <Shield className="h-5 w-5" />
              <span>Acesso seguro e criptografado</span>
            </div>
          </div>
        </section>
      </div>
    </div>
  )
}
