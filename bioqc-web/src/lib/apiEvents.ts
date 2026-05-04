export function emitApiError(message: string) {
  window.dispatchEvent(new CustomEvent('app:api-error', { detail: message }))
}
