import { AreaQcModule } from './AreaQcModule'

export function ImunologiaArea() {
  return (
    <AreaQcModule
      area="imunologia"
      title="Imunologia"
      description="Fluxo especializado para parâmetros e medições de CQ da imunologia, com validação por intervalo ou percentual."
      analytes={['IGG', 'IGM', 'IGA', 'IGE', 'C3', 'C4', 'PCR', 'ASO', 'FR']}
    />
  )
}
