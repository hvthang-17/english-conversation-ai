export type SessionGuard = { onSessionExpired: () => void }
export type Page<T> = { items: T[]; page: number; size: number; total: number }
