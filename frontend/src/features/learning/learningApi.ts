import { useEffect, useState } from 'react'
import { ApiError, getJson } from '../../shared/api/client'

export type SessionGuard = { onSessionExpired: () => void }
export type Page<T> = { items: T[]; page: number; size: number; total: number }
export type ReviewStatus = 'NEEDS_REVIEW' | 'REMEMBERED'
export type Card = {
  id: string; sourceSuggestionId: string | null; expression: string; meaningVi: string
  exampleEn: string | null; status: ReviewStatus
}
export type HistoryEntry = {
  id: string; title: string; level: string; status: string; startedAt: string
  learnerTurns: number; feedbackStatus: string
}
export type Statistics = {
  timeZone: string; completedSessions: number; learnerTurns: number; practiceDays: number
  recentDays: { label: string; count: number }[]
  topics: { topic: string; completedSessions: number; learnerTurns: number }[]
  errorGroups: { label: string; count: number }[]
  recommendations: { scenarioId: string; title: string; level: string; reason: string }[]
}

export function reportError(failure: unknown, onSessionExpired: () => void) {
  if (failure instanceof ApiError && failure.status === 401) onSessionExpired()
  return failure instanceof Error ? failure.message : 'Chưa hoàn thành được yêu cầu.'
}

export function useLearningData<T>(path: string, onSessionExpired: () => void) {
  const [revision, setRevision] = useState(0)
  const key = `${path}:${revision}`
  const [state, setState] = useState<{ key: string; value?: T; error?: string }>()
  useEffect(() => {
    const controller = new AbortController()
    void getJson(path, controller.signal).then(value => {
      if (!controller.signal.aborted) setState({ key, value: value as T })
    }).catch(failure => {
      if (!controller.signal.aborted) setState({ key, error: reportError(failure, onSessionExpired) })
    })
    return () => controller.abort()
  }, [path, key, onSessionExpired])
  return { data: state?.key === key ? state.value : undefined,
    error: state?.key === key ? state.error : undefined, reload: () => setRevision(value => value + 1) }
}
