import { useCallback, useEffect, useState } from 'react'
import { getJson } from '../api/client'
import { reportError } from '../api/errors'

export function useApiQuery<T>(path: string, onSessionExpired: () => void) {
  const [revision, setRevision] = useState(0)
  const reload = useCallback(() => setRevision(value => value + 1), [])
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
      error: state?.key === key ? state.error : undefined, reload }
}
