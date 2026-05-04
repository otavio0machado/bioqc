import { AreaQcModule } from './AreaQcModule'

export function UroanaliseArea() {
  return (
    <AreaQcModule
      area="uroanalise"
      title="Uroanálise"
      description="Módulo dedicado à uroanálise com parâmetros reutilizáveis, histórico de medições e exportação rápida em PDF."
      analytes={['PH', 'DENSIDADE', 'PROTEINAS', 'GLICOSE', 'HEMOGLOBINA', 'LEUCOCITOS', 'NITRITO', 'CETONAS']}
    />
  )
}
