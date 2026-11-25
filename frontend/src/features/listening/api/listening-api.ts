import { mutateJson } from '../../../shared/api/client'

export type Exercise = { id: string; title: string; instructions: string; level: 'A2' | 'B1' }
export type Version = Pick<Exercise, 'id' | 'title' | 'level'> & { lessonId: string; status: string }
export type Question = {
  code: string; type: 'MULTIPLE_CHOICE' | 'FILL_BLANK' | 'REORDER' | 'DICTATION'
  prompt: string; options: { code: string; text: string }[]
}
export type Answer = string | string[]
export type Answers = Record<string, Answer>
export type Result = {
  score: number; maxScore: number; transcriptEn: string; translationVi: string | null
  details: { questionCode: string; answerValue: Answer | null; pointsAwarded: number; accepted: string[]; explanationVi: string }[]
}
export type Attempt = {
  id: string; exercise: Exercise; status: 'IN_PROGRESS' | 'SUBMITTED' | 'ABANDONED'; draftVersion: number
  questions: Question[]; answers: Answers; result: Result | null; startedAt: string
}
export type History = Pick<Attempt, 'id' | 'exercise' | 'startedAt' | 'status'> & { score: number | null; maxScore: number }

export const attemptPath = 'listening/attempts'
export const publicationPath = 'admin/listening-exercises'

export function startExercise(id: string, clientRequestId: string) {
  return mutateJson(`listening/exercises/${id}/attempts`, { clientRequestId }) as Promise<Attempt>
}

export function writeAnswers(id: string, draftVersion: number, answers: Answers, submit: boolean) {
  return mutateJson(`${attemptPath}/${id}/${submit ? 'submit' : 'draft'}`, { draftVersion, answers },
    submit ? 'POST' : 'PUT') as Promise<Attempt>
}
