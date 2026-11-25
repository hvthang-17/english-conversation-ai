import { useEffect, useState } from 'react'
import { getProfile, type Profile } from '../../features/profile/api/profile-api'
import { apiUrl } from '../../shared/api/client'
import { profileChangedEvent } from '../../shared/lib/profile-events'

export function HeaderAccount({ email, isAdmin }: { email: string; isAdmin: boolean }) {
  const [profile, setProfile] = useState<Profile | null>(null)
  const [revision, setRevision] = useState(0)
  useEffect(() => {
    const changed = () => setRevision(value => value + 1)
    window.addEventListener(profileChangedEvent, changed)
    return () => window.removeEventListener(profileChangedEvent, changed)
  }, [])
  useEffect(() => {
    const controller = new AbortController()
    getProfile(controller.signal).then(value => {
      if (!controller.signal.aborted) setProfile(value)
    }).catch(() => { /* Thiếu hồ sơ không ngăn điều hướng; dùng tên/ảnh thay thế. */ })
    return () => controller.abort()
  }, [revision])
  const name = profile?.displayName || email
  return <a className="topbar-account" href="#/profile" aria-label={`Mở hồ sơ học tập: ${name}`}>
    <span className="topbar-account-copy"><strong>{name}</strong><small>{isAdmin ? 'Quản trị viên' : 'Người học'}</small></span>
    <HeaderAvatar key={revision} hasAvatar={Boolean(profile?.avatarUrl)} initial={(profile?.displayName || email).charAt(0).toUpperCase()} revision={revision} />
  </a>
}

function HeaderAvatar({ hasAvatar, initial, revision }: { hasAvatar: boolean; initial: string; revision: number }) {
  const [failed, setFailed] = useState(false)
  return <span className="header-avatar" aria-hidden="true">{hasAvatar && !failed
    ? <img src={`${apiUrl('profile/me/avatar')}?v=${revision}`} alt="" width={44} height={44} onError={() => setFailed(true)} />
    : initial}</span>
}
