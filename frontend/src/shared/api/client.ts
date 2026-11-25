const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || '/api/v1').replace(/\/$/, '')
export function apiUrl(path: string) { return `${apiBaseUrl}/${path.replace(/^\//, '')}` }
export class ApiError extends Error {
  constructor(public readonly status: number, public readonly code = 'HTTP_ERROR', message = `Máy chủ trả về lỗi HTTP ${status}.`, public readonly fieldErrors: Record<string, string> = {}) {
    super(message); this.name = 'ApiError'
  }
}
async function request(path: string, init: RequestInit = {}): Promise<unknown> {
  const response = await fetch(apiUrl(path), { ...init, credentials: 'same-origin', headers: { Accept: 'application/json', ...init.headers } })
  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as { code?: string; message?: string; fieldErrors?: Record<string, string> }
    throw new ApiError(response.status, body.code, body.message, body.fieldErrors)
  }
  if (response.status === 204) return undefined
  const text = await response.text()
  return text ? JSON.parse(text) as unknown : undefined
}
export function getJson(path: string, signal?: AbortSignal): Promise<unknown> { return request(path, { signal }) }
// Fetch a fresh masked token for every mutation, including login/logout. No automatic replay.
async function csrfHeaders(signal?: AbortSignal) {
  const csrf = await getJson('auth/csrf', signal) as { token: string; headerName: string }
  if (typeof csrf.token !== 'string' || typeof csrf.headerName !== 'string') throw new Error('Phản hồi CSRF không hợp lệ.')
  return { [csrf.headerName]: csrf.token }
}
export async function mutateJson(path: string, body?: unknown, method: 'POST' | 'PUT' | 'PATCH' | 'DELETE' = 'POST', signal?: AbortSignal): Promise<unknown> {
  return request(path, { method, signal, headers: { 'Content-Type': 'application/json', ...await csrfHeaders(signal) }, body: body === undefined ? undefined : JSON.stringify(body) })
}
export async function mutateForm(path: string, body: FormData, signal: AbortSignal): Promise<unknown> {
  return request(path, { method: 'POST', signal, headers: await csrfHeaders(signal), body })
}

export async function uploadForm(path: string, body: FormData): Promise<unknown> {
  return request(path, { method: 'PUT', headers: await csrfHeaders(), body })
}
