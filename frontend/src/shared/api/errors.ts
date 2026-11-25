import { ApiError } from './client'

export function reportError(failure: unknown, onSessionExpired: () => void) {
  if (failure instanceof ApiError && failure.status === 401) onSessionExpired()
  return failure instanceof Error ? failure.message : 'Chưa hoàn thành được yêu cầu.'
}

