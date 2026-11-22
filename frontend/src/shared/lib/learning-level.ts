export type Level = 'A2' | 'B1'
export function isLevel(value: unknown): value is Level { return value === 'A2' || value === 'B1' }
