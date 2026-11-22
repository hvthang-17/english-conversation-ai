import { getJson } from '../../../shared/api/client'
import type { HealthResponse } from '../types'

export async function getBackendHealth(signal: AbortSignal): Promise<HealthResponse> {
  const data = await getJson('system/health', signal)
  if (typeof data !== 'object' || data === null ||
    !('status' in data) || data.status !== 'UP' ||
    !('application' in data) || typeof data.application !== 'string' ||
    !('timestamp' in data) || typeof data.timestamp !== 'string') {
    throw new Error('Phản hồi kiểm tra kết nối không hợp lệ.')
  }
  return { status: data.status, application: data.application, timestamp: data.timestamp }
}
