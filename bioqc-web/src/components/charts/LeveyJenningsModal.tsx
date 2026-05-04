import { lazy, Suspense, useMemo, useState } from 'react'
import type { QcExam } from '../../types'
import { Modal, Select, Skeleton } from '../ui'

const LeveyJenningsChart = lazy(() =>
  import('./LeveyJenningsChart').then((module) => ({ default: module.LeveyJenningsChart })),
)

interface LeveyJenningsModalProps {
  isOpen: boolean
  onClose: () => void
  area: string
  exams: QcExam[]
}

export function LeveyJenningsModal({ isOpen, onClose, area, exams }: LeveyJenningsModalProps) {
  const [selectedExam, setSelectedExam] = useState('')
  const [selectedLevel, setSelectedLevel] = useState('Normal')

  const availableExams = useMemo(
    () => exams.filter((exam) => exam.area === area),
    [area, exams],
  )

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Gráfico Levey-Jennings"
      size="lg"
    >
      <div className="mb-4 rounded-2xl border border-sky-200 bg-sky-50 px-4 py-3 text-sm text-sky-900">
        O contrato atual deste grafico usa os ultimos 30 registros canonicos de CQ para o exame, nivel e area selecionados.
        Eventos de pos-calibracao nao entram nesta curva.
      </div>
      <div className="mb-6 grid gap-4 md:grid-cols-2">
        <Select
          label="Exame"
          value={selectedExam}
          onChange={(event) => setSelectedExam(event.target.value)}
        >
          <option value="">Selecione</option>
          {availableExams.map((exam) => (
            <option key={exam.id} value={exam.name}>
              {exam.name}
            </option>
          ))}
        </Select>
        <Select
          label="Nível"
          value={selectedLevel}
          onChange={(event) => setSelectedLevel(event.target.value)}
        >
          {['Normal', 'Patológico', 'Alto', 'Baixo'].map((level) => (
            <option key={level} value={level}>
              {level}
            </option>
          ))}
        </Select>
      </div>
      {selectedExam ? (
        <Suspense fallback={<Skeleton height="24rem" />}>
          <LeveyJenningsChart examName={selectedExam} level={selectedLevel} area={area} />
        </Suspense>
      ) : null}
    </Modal>
  )
}
