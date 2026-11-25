import { getJson } from '../../../shared/api/client'
import { isLevel, type Level } from '../../../shared/lib/learning-level'
export type Topic = { id: string; code: string; name: string; description: string | null }
export type Scenario = { id: string; code: string; topicId: string; topicName: string; title: string; description: string; levels: Level[] }
export type ScenarioRole = { code: string; learnerRole: string; aiRole: string; communicationGoal: string; contextDescription: string }
export type ScenarioDetail = Scenario & Omit<ScenarioRole, 'code'> & { roles: ScenarioRole[] }
export type ScenarioPage = { content: Scenario[]; page: number; size: number; totalElements: number }
function isObject(value: unknown): value is Record<string, unknown> { return typeof value === 'object' && value !== null }
function scenario(value: unknown): Scenario {
  if (!isObject(value) || !['id', 'code', 'topicId', 'topicName', 'title', 'description'].every(key => typeof value[key] === 'string') || !Array.isArray(value.levels) || !value.levels.every(isLevel)) throw new Error('Dữ liệu tình huống không hợp lệ.')
  return value as Scenario
}
export async function getTopics(signal?: AbortSignal): Promise<Topic[]> {
  const value = await getJson('topics', signal)
  if (!Array.isArray(value) || !value.every(t => isObject(t) && ['id', 'code', 'name'].every(key => typeof t[key] === 'string') && (t.description === null || typeof t.description === 'string'))) throw new Error('Dữ liệu chủ đề không hợp lệ.')
  return value as Topic[]
}
export async function getScenarios(level: Level, topicId: string, page: number, signal?: AbortSignal): Promise<ScenarioPage> {
  const query = new URLSearchParams({ level, page: String(page), size: '8' }); if (topicId) query.set('topicId', topicId)
  const value = await getJson(`scenarios?${query}`, signal)
  if (!isObject(value) || !Array.isArray(value.content) || !Number.isInteger(value.page) || !Number.isInteger(value.size) || !Number.isInteger(value.totalElements)) throw new Error('Dữ liệu danh sách không hợp lệ.')
  return { content: value.content.map(scenario), page: value.page as number, size: value.size as number, totalElements: value.totalElements as number }
}
export async function getScenario(id: string, level: Level, signal?: AbortSignal): Promise<ScenarioDetail> {
  const value = await getJson(`scenarios/${encodeURIComponent(id)}?level=${level}`, signal)
  scenario(value)
  const fields = ['learnerRole', 'aiRole', 'communicationGoal', 'contextDescription']
  if (!isObject(value) || !fields.every(key => typeof value[key] === 'string')) throw new Error('Dữ liệu chi tiết không hợp lệ.')
  if (value.roles !== undefined && (!Array.isArray(value.roles) || !value.roles.every(role =>
    isObject(role) && ['code', ...fields].every(key => typeof role[key] === 'string'))))
    throw new Error('Danh sách vai giao tiếp không hợp lệ.')
  const detail = value as ScenarioDetail
  return { ...detail, roles: detail.roles ?? [{
    code: 'DEFAULT', learnerRole: detail.learnerRole, aiRole: detail.aiRole,
    communicationGoal: detail.communicationGoal, contextDescription: detail.contextDescription,
  }] }
}
