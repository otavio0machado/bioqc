import { AreaQcModule } from './AreaQcModule'

export function MicrobiologiaArea() {
  return (
    <AreaQcModule
      area="microbiologia"
      title="Microbiologia"
      description="Rotina de CQ para microbiologia com monitoramento de analitos e aprovação automática por parâmetro ativo."
      analytes={['CULTURA', 'ANTIBIOGRAMA', 'TSA', 'GRAM', 'BK']}
    />
  )
}
