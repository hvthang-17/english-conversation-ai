export type Topic = { id: string; code: string; name: string; description: string | null; active: boolean; sortOrder: number }
export type Scenario = { id: string; status: 'DRAFT' | 'ACTIVE' | 'ARCHIVED'; levels: string[] }
  & Record<typeof scenarioFields[number][0], string>
export const scenarioFields = [
  ['code', 'Mã tình huống', 80],
  ['title', 'Tên tình huống', 200],
  ['description', 'Mô tả', 2000],
  ['learnerRole', 'Vai người học', 200],
  ['aiRole', 'Vai đối phương', 200],
  ['communicationGoal', 'Mục tiêu giao tiếp', 1000],
  ['contextDescription', 'Bối cảnh', 2000],
  ['aiInstruction', 'Hướng dẫn nội bộ cho AI', 4000],
] as const
export const variantFields = scenarioFields.filter(([name]) => name !== 'title' && name !== 'description')
export type ScenarioVariant = Record<Exclude<typeof scenarioFields[number][0], 'title' | 'description'>, string>
export const statuses = { DRAFT: 'Bản nháp', ACTIVE: 'Công khai', ARCHIVED: 'Đã ẩn' } as const

