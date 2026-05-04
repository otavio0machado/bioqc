import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import type { ReportFilterField, ReportFilterSpec } from '../../types/reportsV2'
import { DynamicFilterForm } from './DynamicFilterForm'

function Harness({
  fields,
  onChangeSpy,
}: {
  fields: ReportFilterField[]
  onChangeSpy?: (values: Record<string, unknown>) => void
}) {
  const [values, setValues] = useState<Record<string, unknown>>({})
  const spec: ReportFilterSpec = { fields }
  return (
    <DynamicFilterForm
      filterSpec={spec}
      values={values}
      onChange={(next) => {
        setValues(next)
        onChangeSpy?.(next)
      }}
    />
  )
}

describe('DynamicFilterForm', () => {
  it('renderiza INTEGER como input number e marca required com *', () => {
    render(
      <Harness
        fields={[
          {
            key: 'month',
            type: 'INTEGER',
            required: true,
            allowedValues: null,
            label: 'Mes',
            helpText: null,
          },
        ]}
      />,
    )

    const input = screen.getByLabelText('Mes *') as HTMLInputElement
    expect(input).toBeInTheDocument()
    expect(input.type).toBe('number')
    expect(input.min).toBe('1')
    expect(input.max).toBe('12')
  })

  it('renderiza STRING_ENUM como select com options do allowedValues', () => {
    render(
      <Harness
        fields={[
          {
            key: 'area',
            type: 'STRING_ENUM',
            required: true,
            allowedValues: ['bioquimica', 'hematologia'],
            label: 'Area',
            helpText: null,
          },
        ]}
      />,
    )

    const select = screen.getByLabelText('Area *') as HTMLSelectElement
    expect(select).toBeInTheDocument()
    expect(select.tagName).toBe('SELECT')
    const options = Array.from(select.options).map((opt) => opt.value)
    expect(options).toContain('bioquimica')
    expect(options).toContain('hematologia')
  })

  it('dispara onChange com valor numerico tipado para INTEGER', async () => {
    const spy = vi.fn()
    render(
      <Harness
        fields={[
          {
            key: 'year',
            type: 'INTEGER',
            required: false,
            allowedValues: null,
            label: 'Ano',
            helpText: null,
          },
        ]}
        onChangeSpy={spy}
      />,
    )

    const input = screen.getByLabelText('Ano') as HTMLInputElement
    await userEvent.type(input, '2026')

    // O ultimo onChange deve conter year como number, nao string.
    const lastCall = spy.mock.calls.at(-1)?.[0]
    expect(lastCall).toEqual({ year: 2026 })
    expect(typeof lastCall?.year).toBe('number')
  })

  it('nao marca com * quando required=false', () => {
    render(
      <Harness
        fields={[
          {
            key: 'month',
            type: 'INTEGER',
            required: false,
            allowedValues: null,
            label: 'Mes opcional',
            helpText: null,
          },
        ]}
      />,
    )
    expect(screen.getByLabelText('Mes opcional')).toBeInTheDocument()
    expect(screen.queryByLabelText('Mes opcional *')).not.toBeInTheDocument()
  })

  it('renderiza BOOLEAN como checkbox', () => {
    render(
      <Harness
        fields={[
          {
            key: 'includeWarnings',
            type: 'BOOLEAN',
            required: false,
            allowedValues: null,
            label: 'Incluir avisos',
            helpText: null,
          },
        ]}
      />,
    )

    const checkbox = screen.getByRole('checkbox', { name: /Incluir avisos/i }) as HTMLInputElement
    expect(checkbox).toBeInTheDocument()
    expect(checkbox.type).toBe('checkbox')
  })
})
