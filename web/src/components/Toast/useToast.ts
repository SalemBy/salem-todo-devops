import { createContext, useContext } from 'react'

export interface ToastMessage {
  title: string
  description?: string
  tone?: 'neutral' | 'success' | 'danger'
}

export interface ToastApi {
  show: (message: ToastMessage) => void
}

export const ToastContext = createContext<ToastApi | null>(null)

export function useToast(): ToastApi {
  const api = useContext(ToastContext)
  if (!api) throw new Error('useToast must be used inside ToastProvider')
  return api
}
