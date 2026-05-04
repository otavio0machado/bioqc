import { z } from 'zod'

const strongPasswordMessage =
  'A senha deve ter pelo menos 8 caracteres, com letra maiuscula, minuscula e numero.'

export const loginSchema = z.object({
  username: z.string().trim().min(3, 'O nome de usuario deve ter pelo menos 3 caracteres.'),
  password: z.string().min(4, 'A senha deve ter pelo menos 4 caracteres.'),
})

export const resetPasswordSchema = z
  .object({
    password: z
      .string()
      .min(8, strongPasswordMessage)
      .regex(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/, strongPasswordMessage),
    confirmPassword: z.string(),
  })
  .refine((value) => value.password === value.confirmPassword, {
    message: 'As senhas precisam ser iguais.',
    path: ['confirmPassword'],
  })

export type LoginFormValues = z.infer<typeof loginSchema>
export type ResetPasswordFormValues = z.infer<typeof resetPasswordSchema>
