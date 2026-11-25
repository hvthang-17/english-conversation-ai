export type Statistics = {
  timeZone: string; completedSessions: number; learnerTurns: number; practiceDays: number
  recentDays: { label: string; count: number }[]
  topics: { topic: string; completedSessions: number; learnerTurns: number }[]
  errorGroups: { label: string; count: number }[]
  recommendations: { scenarioId: string; title: string; level: string; reason: string }[]
}
