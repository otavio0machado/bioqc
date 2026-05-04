import { Mic, Square, WandSparkles } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useVoiceToForm } from '../../hooks/useAiAnalysis'
import type { VoiceFormData, VoiceToFormRequest } from '../../types'
import { Button, Modal, useToast } from '../ui'

interface VoiceRecorderModalProps {
  formType: VoiceToFormRequest['formType']
  title: string
  onApply: (data: VoiceFormData) => void
  buttonLabel?: string
}

export function VoiceRecorderModal({
  formType,
  title,
  onApply,
  buttonLabel = 'Preencher por voz',
}: VoiceRecorderModalProps) {
  const { toast } = useToast()
  const voiceMutation = useVoiceToForm()
  const [isOpen, setIsOpen] = useState(false)
  const [isRecording, setIsRecording] = useState(false)
  const [audioBase64, setAudioBase64] = useState('')
  const [mimeType, setMimeType] = useState('audio/webm')
  const [statusMessage, setStatusMessage] = useState('Toque no microfone para iniciar.')
  const mediaRecorderRef = useRef<MediaRecorder | null>(null)
  const mediaChunksRef = useRef<Blob[]>([])
  const streamRef = useRef<MediaStream | null>(null)

  const stopTracks = () => {
    streamRef.current?.getTracks().forEach((track) => track.stop())
    streamRef.current = null
  }

  useEffect(() => {
    return () => {
      stopTracks()
    }
  }, [])

  const open = () => {
    setAudioBase64('')
    setStatusMessage('Toque no microfone para iniciar.')
    setIsOpen(true)
  }

  const close = () => {
    stopRecording()
    setAudioBase64('')
    setStatusMessage('Toque no microfone para iniciar.')
    setIsOpen(false)
  }

  const startRecording = async () => {
    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
      toast.error('Seu navegador não suporta gravação de áudio.')
      return
    }

    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      const supportedMimeType = getSupportedMimeType()
      const recorder = supportedMimeType ? new MediaRecorder(stream, { mimeType: supportedMimeType }) : new MediaRecorder(stream)
      streamRef.current = stream
      mediaRecorderRef.current = recorder
      mediaChunksRef.current = []
      setMimeType(recorder.mimeType || supportedMimeType || 'audio/webm')

      recorder.ondataavailable = (event) => {
        if (event.data.size > 0) {
          mediaChunksRef.current.push(event.data)
        }
      }

      recorder.onstop = async () => {
        const blob = new Blob(mediaChunksRef.current, { type: recorder.mimeType || supportedMimeType || 'audio/webm' })
        setAudioBase64(await blobToBase64(blob))
        setStatusMessage('Gravação pronta. Analise para preencher os campos.')
        stopTracks()
      }

      recorder.start()
      setIsRecording(true)
      setAudioBase64('')
      setStatusMessage('Gravando... fale os dados do formulário.')
    } catch {
      toast.error('Não foi possível acessar o microfone.')
    }
  }

  const stopRecording = () => {
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
      mediaRecorderRef.current.stop()
    } else {
      stopTracks()
    }
    setIsRecording(false)
  }

  const analyzeAudio = async () => {
    if (!audioBase64) {
      toast.warning('Grave um áudio antes de analisar.')
      return
    }

    try {
      const response = await voiceMutation.mutateAsync({
        audioBase64,
        formType,
        mimeType,
      })
      onApply(response)
      toast.success('Campos preenchidos com sucesso.')
      close()
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Não foi possível processar o áudio.'
      toast.error(message)
    }
  }

  return (
    <>
      <Button variant="secondary" icon={<Mic className="h-4 w-4" />} onClick={open}>
        {buttonLabel}
      </Button>

      <Modal
        isOpen={isOpen}
        onClose={close}
        title={title}
        footer={
          <div className="flex flex-wrap justify-end gap-3">
            <Button variant="ghost" onClick={close}>
              Fechar
            </Button>
            {isRecording ? (
              <Button variant="danger" icon={<Square className="h-4 w-4" />} onClick={stopRecording}>
                Finalizar gravação
              </Button>
            ) : (
              <Button
                icon={<Mic className={`h-4 w-4 ${isRecording ? 'animate-voicePulse' : ''}`} />}
                onClick={() => void startRecording()}
              >
                Gravar áudio
              </Button>
            )}
            <Button
              variant="secondary"
              icon={<WandSparkles className="h-4 w-4" />}
              onClick={() => void analyzeAudio()}
              loading={voiceMutation.isPending}
            >
              Analisar áudio
            </Button>
          </div>
        }
      >
        <div className="space-y-4">
          <div className="rounded-3xl border border-dashed border-green-200 bg-green-50 px-6 py-8 text-center">
            <div
              className={`mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-green-800 text-white ${
                isRecording ? 'animate-voicePulse' : ''
              }`}
            >
              <Mic className="h-7 w-7" />
            </div>
            <p className="mt-4 text-sm font-medium text-green-900">{statusMessage}</p>
            <p className="mt-2 text-sm text-green-800/80">
              Funciona para registro CQ, referências, reagentes e manutenção.
            </p>
          </div>

          {audioBase64 ? (
            <div className="rounded-2xl bg-neutral-50 px-4 py-3 text-sm text-neutral-600">
              Áudio capturado com sucesso. Você já pode analisar e preencher o formulário automaticamente.
            </div>
          ) : null}
        </div>
      </Modal>
    </>
  )
}

function getSupportedMimeType() {
  const candidates = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4']
  return candidates.find((candidate) => MediaRecorder.isTypeSupported?.(candidate))
}

async function blobToBase64(blob: Blob) {
  const buffer = await blob.arrayBuffer()
  let binary = ''
  const bytes = new Uint8Array(buffer)
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte)
  })
  return btoa(binary)
}
