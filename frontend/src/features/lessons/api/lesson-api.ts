import { getJson, mutateJson } from '../../../shared/api/client'
import { isLevel, type Level } from '../../../shared/lib/learning-level'

export type BookmarkKind = 'FAVORITE' | 'SAVE_FOR_LATER'
type Marks = { id: string; favorite: boolean; savedForLater: boolean }
export type AvailableLesson = Marks & {
  available: true
  title: string
  summary: string
  topicName: string
  level: Level
  learningObjective: string
  scenarioId: string | null
  scenarioTitle: string | null
}
export type Lesson = AvailableLesson | (Marks & { available: false })

function lesson(value: unknown): Lesson {
  const data = value as (Omit<Partial<AvailableLesson>, 'available'> & { available?: boolean }) | null
  if (!data || typeof data.id !== 'string' || typeof data.available !== 'boolean' ||
    typeof data.favorite !== 'boolean' || typeof data.savedForLater !== 'boolean') {
    throw new Error('Phản hồi bài học không hợp lệ.')
  }
  if (!data.available) return { id: data.id, available: false, favorite: data.favorite, savedForLater: data.savedForLater }
  if (typeof data.title !== 'string' || typeof data.summary !== 'string' || typeof data.topicName !== 'string' ||
    !isLevel(data.level) || typeof data.learningObjective !== 'string' ||
    !(data.scenarioId === null || typeof data.scenarioId === 'string') ||
    !(data.scenarioTitle === null || typeof data.scenarioTitle === 'string')) {
    throw new Error('Phản hồi bài học không hợp lệ.')
  }
  return data as AvailableLesson
}

export type LessonPage = { items: Lesson[]; page: number; size: number; total: number }

export async function getLessons(query: URLSearchParams, kind: BookmarkKind | null, signal: AbortSignal) {
  const search = new URLSearchParams(query)
  if (kind) search.set('kind', kind)
  const value = await getJson(`lessons${kind ? '/saved' : ''}?${search}`, signal) as LessonPage | null
  if (!value || !Array.isArray(value.items) || !Number.isInteger(value.page) ||
    !Number.isInteger(value.size) || !Number.isInteger(value.total)) {
    throw new Error('Danh sách bài học không hợp lệ.')
  }
  return { ...value, items: value.items.map(lesson) }
}

export async function getLesson(id: string, signal: AbortSignal) {
  const item = lesson(await getJson(`lessons/${id}`, signal))
  if (!item.available) throw new Error('Bài học không còn khả dụng.')
  return item
}

export function setBookmark(id: string, kind: BookmarkKind, marked: boolean) {
  return mutateJson(`lessons/${id}/bookmarks/${kind}`, undefined, marked ? 'PUT' : 'DELETE')
}
