export type ReviewStatus = 'NEEDS_REVIEW' | 'REMEMBERED'
export type Card = {
  id: string; sourceSuggestionId: string | null; expression: string; meaningVi: string
  exampleEn: string | null; status: ReviewStatus
}
