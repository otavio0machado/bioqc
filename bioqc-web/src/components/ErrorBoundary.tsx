import { AlertTriangle, RefreshCcw } from 'lucide-react'
import { Component, type ErrorInfo, type PropsWithChildren } from 'react'
import { Button, Card } from './ui'

interface ErrorBoundaryState {
  hasError: boolean
}

export class ErrorBoundary extends Component<PropsWithChildren, ErrorBoundaryState> {
  state: ErrorBoundaryState = {
    hasError: false,
  }

  static getDerivedStateFromError() {
    return { hasError: true }
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('Unhandled React error', error, errorInfo)
  }

  private retry = () => {
    this.setState({ hasError: false })
  }

  private reload = () => {
    window.location.reload()
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="flex min-h-screen items-center justify-center bg-neutral-50 px-6 py-12">
          <Card className="w-full max-w-lg space-y-5 border border-red-100 bg-white p-8 text-center shadow-sm">
            <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-red-50 text-red-600">
              <AlertTriangle className="h-7 w-7" />
            </div>
            <div className="space-y-2">
              <h1 className="text-2xl font-semibold text-neutral-950">Algo deu errado, tente recarregar</h1>
              <p className="text-sm leading-6 text-neutral-600">
                A interface encontrou um erro inesperado. Você pode tentar novamente agora ou recarregar a aplicação.
              </p>
            </div>
            <div className="flex flex-col gap-3 sm:flex-row sm:justify-center">
              <Button onClick={this.retry} className="sm:min-w-40">
                <RefreshCcw className="h-4 w-4" />
                Tentar novamente
              </Button>
              <Button variant="secondary" onClick={this.reload} className="sm:min-w-40">
                Recarregar página
              </Button>
            </div>
          </Card>
        </div>
      )
    }

    return this.props.children
  }
}
