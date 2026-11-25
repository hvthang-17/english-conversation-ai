import { mutateJson } from '../../../../shared/api/client'
import type { Account } from '../types'

export function updateAccountAccess(id: string, change: { role?: Account['role']; status?: Account['status'] }) {
  return mutateJson(`admin/users/${id}/access`, change, 'PATCH')
}
