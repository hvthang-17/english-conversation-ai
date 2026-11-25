export const accountPages = ['account', 'data', 'profile']
export const catalogPages = ['lessons', 'lesson', 'saved-lessons', 'scenarios', 'scenario']
export const laterPages = ['practice', 'admin', 'review', 'history', 'stats']
export const listeningPages = ['listening', 'listening-attempt']
export const protectedPages = [...accountPages, ...catalogPages, 'notebook', 'pronunciation', ...listeningPages, ...laterPages]
const namedPages = ['forgot-password', 'reset-password', 'register',
  ...protectedPages.filter(page => !['lesson', 'scenario', 'practice', 'listening-attempt'].includes(page))]

export function route(hash = window.location.hash) {
  const path = hash.split('?')[0]?.slice(2) ?? ''
  if (/^lessons\/[1-9][0-9]*$/.test(path)) return 'lesson'
  if (/^scenarios\/[1-9][0-9]*$/.test(path)) return 'scenario'
  if (/^practice\/[1-9][0-9]*$/.test(path)) return 'practice'
  if (/^listening\/attempts\/[1-9][0-9]*$/.test(path)) return 'listening-attempt'
  return namedPages.includes(path) ? path : 'login'
}

export function startPage(catalog: boolean, learning: boolean) {
  if (catalog) return '/lessons'
  return learning ? '/scenarios' : '/profile'
}

