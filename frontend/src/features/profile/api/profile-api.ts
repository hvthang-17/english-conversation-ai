import { getJson, mutateJson } from '../../../shared/api/client'
import { isLevel, type Level } from '../../../shared/lib/learning-level'
export type Profile = { userId: string; displayName: string; selectedLevel: Level; learningGoal: string | null; dailyGoalMinutes: number | null; topicIds: string[] }
export type ProfileInput = Omit<Profile, 'userId'>
function profile(value: unknown): Profile {
  const p = value as Partial<Profile> | null
  if (!p || typeof p.userId !== 'string' || typeof p.displayName !== 'string' || !isLevel(p.selectedLevel) || !(p.learningGoal === null || typeof p.learningGoal === 'string') || !(p.dailyGoalMinutes === null || (typeof p.dailyGoalMinutes === 'number' && Number.isInteger(p.dailyGoalMinutes))) || !Array.isArray(p.topicIds) || !p.topicIds.every(id => typeof id === 'string')) throw new Error('Dữ liệu hồ sơ không hợp lệ.')
  return p as Profile
}
export async function getProfile(signal?: AbortSignal): Promise<Profile> { return profile(await getJson('profile/me', signal)) }
export async function saveProfile(input: ProfileInput): Promise<Profile> { return profile(await mutateJson('profile/me', input, 'PUT')) }
