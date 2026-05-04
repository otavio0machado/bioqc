import { AreaQcModule } from './AreaQcModule'

export function ParasitologiaArea() {
  return (
    <AreaQcModule
      area="parasitologia"
      title="Parasitologia"
      description="Controle de qualidade de parasitologia com cadastro de parâmetros, faixas operacionais e histórico de medições."
      analytes={['EPF', 'GIARDIA', 'ENTAMOEBA', 'CRYPTOSPORIDIUM', 'ISOSPORA']}
    />
  )
}
