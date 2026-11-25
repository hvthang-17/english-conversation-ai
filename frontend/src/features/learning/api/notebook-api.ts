import { mutateJson } from '../../../shared/api/client'
import type { ReviewStatus } from '../../review/types'

export type NotebookCard = {
  id: string; sourceLanguageItemId: string | null; itemType: 'VOCABULARY' | 'PHRASE'
  expression: string; meaningVi: string; exampleEn: string | null; ipa: string | null
  status: ReviewStatus; lastReviewedAt: string | null
}

export type ReviewAttempt = {
  id: string; mode: 'FLASHCARD' | 'QUICK_QUIZ'; result: ReviewStatus | null
  correct: boolean | null; reviewedAt: string
}

export type Quiz = {
  id: string; expression: string; options: { id: string; meaningVi: string }[]; expiresAt: string
}

type ReviewInput = { mode: 'FLASHCARD'; result: ReviewStatus; clientRequestId: string }
  | { mode: 'QUICK_QUIZ'; optionId: string; clientRequestId: string }

export const notebookPath = 'learning/notebook'

export function saveLanguageItem(id: string) {
  return mutateJson(`${notebookPath}/from-language-item/${id}`) as Promise<NotebookCard>
}

export function openQuiz(cardId: string) {
  return mutateJson(`${notebookPath}/${cardId}/quiz`) as Promise<Quiz>
}

export function submitReview(cardId: string, input: ReviewInput) {
  return mutateJson(`${notebookPath}/${cardId}/attempts`, input) as Promise<ReviewAttempt>
}

export function deleteCard(cardId: string) {
  return mutateJson(`${notebookPath}/${cardId}`, undefined, 'DELETE')
}
