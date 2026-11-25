export type User = { id: string; email: string; role: 'LEARNER' | 'ADMIN'; status: 'ACTIVE' | 'LOCKED' }
export function userResponse(value: unknown): User {
  const u = value as Partial<User> | null
  if (!u || typeof u.id !== 'string' || typeof u.email !== 'string' || !['LEARNER', 'ADMIN'].includes(u.role ?? '') || !['ACTIVE', 'LOCKED'].includes(u.status ?? '')) throw new Error('Phản hồi tài khoản không hợp lệ.')
  return u as User
}
