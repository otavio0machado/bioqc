import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { Combobox, type ComboboxOption } from './Combobox'

const OPTIONS: ComboboxOption[] = [
  { value: 'Glicose', label: 'Glicose' },
  { value: 'Colesterol', label: 'Colesterol Total' },
  { value: 'Ureia', label: 'Ureia' },
]

function Harness({
  initial = '',
  allowCustom = true,
  onChange,
}: {
  initial?: string
  allowCustom?: boolean
  onChange?: (v: string) => void
}) {
  const [value, setValue] = useState(initial)
  return (
    <Combobox
      label="Exame"
      placeholder="Busque..."
      value={value}
      onChange={(v) => {
        setValue(v)
        onChange?.(v)
      }}
      options={OPTIONS}
      allowCustom={allowCustom}
    />
  )
}

describe('Combobox', () => {
  it('abre lista ao focar e lista todas as opcoes', async () => {
    render(<Harness />)
    const input = screen.getByPlaceholderText('Busque...') as HTMLInputElement
    await userEvent.click(input)
    expect(await screen.findByRole('option', { name: /Glicose/ })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: /Colesterol Total/ })).toBeInTheDocument()
  })

  it('filtra por texto insensivel a acento e caixa', async () => {
    render(<Harness />)
    const input = screen.getByPlaceholderText('Busque...') as HTMLInputElement
    await userEvent.click(input)
    await userEvent.type(input, 'col')
    expect(await screen.findByRole('option', { name: /Colesterol Total/ })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: /Glicose/ })).not.toBeInTheDocument()
  })

  it('oferece criar novo item quando allowCustom e busca nao casa nada', async () => {
    const onChange = vi.fn()
    render(<Harness onChange={onChange} />)
    const input = screen.getByPlaceholderText('Busque...') as HTMLInputElement
    await userEvent.click(input)
    await userEvent.type(input, 'TSH')
    const createOption = await screen.findByText(/Criar novo/)
    expect(createOption).toBeInTheDocument()
  })

  it('nao mostra opcao criar quando allowCustom=false', async () => {
    render(<Harness allowCustom={false} />)
    const input = screen.getByPlaceholderText('Busque...') as HTMLInputElement
    await userEvent.click(input)
    await userEvent.type(input, 'XYZ')
    expect(screen.queryByText(/Criar novo/)).not.toBeInTheDocument()
  })

  it('seleciona opcao ao clicar e dispara onChange com o value', async () => {
    const onChange = vi.fn()
    render(<Harness onChange={onChange} />)
    const input = screen.getByPlaceholderText('Busque...') as HTMLInputElement
    await userEvent.click(input)
    await userEvent.click(await screen.findByRole('option', { name: /Ureia/ }))
    expect(onChange).toHaveBeenCalledWith('Ureia')
  })
})
