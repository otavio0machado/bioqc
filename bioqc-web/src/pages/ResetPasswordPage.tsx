import { zodResolver } from '@hookform/resolvers/zod'
import { Eye, EyeOff, KeyRound, Lock } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, Navigate, useSearchParams } from 'react-router-dom'
import { Button, Card, Input, useToast } from '../components/ui'
import { useAuth } from '../hooks/useAuth'
import { type ResetPasswordFormValues, resetPasswordSchema } from '../lib/authSchemas'
import { authService } from '../services/authService'
import logoBio from '../assets/logobio.png'

export function ResetPasswordPage() {
  const { isAuthenticated } = useAuth()
  const { toast } = useToast()
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)
  const [wasReset, setWasReset] = useState(false)
  const form = useForm<ResetPasswordFormValues>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: {
      password: '',
      confirmPassword: '',
    },
    mode: 'onChange',
  })

  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />
  }

  const handleSubmit = form.handleSubmit(async (data) => {
    if (!token) {
      toast.error('O link de recuperação está incompleto ou inválido.')
      return
    }

    try {
      const response = await authService.resetPassword({
        token,
        newPassword: data.password,
      })
      toast.success(response.message)
      setWasReset(true)
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Não foi possível redefinir a senha.'
      toast.error(message)
    }
  })

  return (
    <div className="min-h-screen bg-neutral-50">
      <div className="grid min-h-screen lg:grid-cols-[1.15fr_0.85fr]">
        <section className="relative hidden overflow-hidden bg-gradient-to-br from-emerald-950 via-green-900 to-lime-800 px-10 py-12 text-white lg:flex">
          <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_left,_rgba(255,255,255,0.2),_transparent_28%)]" />
          <div className="relative flex h-full w-full items-center">
            <div className="max-w-2xl space-y-8">
              <div className="inline-flex items-center gap-3 rounded-full border border-white/20 bg-white/10 px-4 py-2 text-sm font-medium backdrop-blur-md">
                <img src={logoBio} alt="Biodiagnóstico" className="h-6 w-6 rounded-full bg-white/90 p-0.5" />
                Recuperação de Acesso
              </div>
              <div className="space-y-5">
                <h1 className="max-w-xl text-5xl font-bold leading-tight">
                  Redefina sua senha com segurança e volte ao fluxo operacional.
                </h1>
                <p className="max-w-lg text-lg text-green-50/85">
                  O novo acesso entra em vigor assim que a senha for atualizada. O link de recuperação expira automaticamente.
                </p>
              </div>
              <Card glass className="max-w-xl text-white">
                <p className="text-sm leading-6 text-green-50/90">
                  Use uma senha forte para proteger seus registros de CQ, relatórios e rotinas laboratoriais.
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
              <h2 className="text-3xl font-bold text-neutral-900">Redefinir senha</h2>
              <p className="text-neutral-500">Crie uma nova senha para continuar usando a plataforma.</p>
            </div>

            {wasReset ? (
              <Card className="space-y-4 border border-green-100 bg-green-50 text-green-950">
                <div className="flex items-center gap-3">
                  <KeyRound className="h-5 w-5" />
                  <div className="font-semibold">Senha atualizada com sucesso.</div>
                </div>
                <p className="text-sm text-green-900">
                  Você já pode voltar para a tela de login e acessar com a nova senha.
                </p>
                <Link className="inline-flex text-sm font-semibold text-green-900 underline" to="/login">
                  Voltar para o login
                </Link>
              </Card>
            ) : (
              <form className="space-y-5" onSubmit={handleSubmit}>
                {!token ? (
                  <Card className="border border-amber-200 bg-amber-50 text-amber-950">
                    Este link não contém um token válido. Solicite uma nova recuperação na tela de login.
                  </Card>
                ) : null}

                <div className="relative">
                  <Input
                    label="Nova senha"
                    type={showPassword ? 'text' : 'password'}
                    placeholder="Digite sua nova senha"
                    icon={<Lock className="h-4 w-4" />}
                    error={form.formState.errors.password?.message}
                    autoComplete="new-password"
                    {...form.register('password')}
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

                <div className="relative">
                  <Input
                    label="Confirmar nova senha"
                    type={showConfirmPassword ? 'text' : 'password'}
                    placeholder="Repita a nova senha"
                    icon={<KeyRound className="h-4 w-4" />}
                    error={form.formState.errors.confirmPassword?.message}
                    autoComplete="new-password"
                    {...form.register('confirmPassword')}
                  />
                  <button
                    type="button"
                    className="absolute right-3 top-[2.65rem] rounded-full p-1 text-neutral-400 transition hover:text-neutral-700"
                    onClick={() => setShowConfirmPassword((value) => !value)}
                    aria-label={showConfirmPassword ? 'Ocultar confirmação de senha' : 'Mostrar confirmação de senha'}
                  >
                    {showConfirmPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>

                <Button type="submit" size="xl" className="w-full" loading={form.formState.isSubmitting} disabled={!token}>
                  Salvar nova senha
                </Button>
              </form>
            )}

            <Link className="inline-flex text-sm font-medium text-green-800 transition hover:text-green-900" to="/login">
              Voltar para o login
            </Link>
          </div>
        </section>
      </div>
    </div>
  )
}
