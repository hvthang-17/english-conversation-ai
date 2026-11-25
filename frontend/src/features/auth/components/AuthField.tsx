import { useState } from 'react'
import { Icon } from '../../../shared/ui/Icon'

type Props = { name: string; label: string; autoComplete: string; type?: 'email' | 'password'; minLength?: number; error?: string; hint?: string }

export function AuthField({ name, label, autoComplete, type = 'password', minLength, error, hint }: Props) {
  const [visible, setVisible] = useState(false)
  const description = [hint ? `${name}-hint` : '', error ? `${name}-error` : ''].filter(Boolean).join(' ') || undefined
  return <div className="form-field auth-field">
    <label htmlFor={name}>{label}</label>
    <div className="auth-input-row">
      <input id={name} name={name} type={type === 'password' && visible ? 'text' : type} autoComplete={autoComplete}
        required maxLength={type === 'email' ? 254 : 128} minLength={minLength}
        aria-invalid={Boolean(error)} aria-describedby={description} autoCapitalize="none" spellCheck={false} />
      {type === 'password' ? <button className="auth-password-toggle" type="button" aria-controls={name}
        aria-label={`${visible ? 'Ẩn' : 'Hiện'} ${label.toLocaleLowerCase('vi')}`} title={visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'} onClick={() => setVisible(value => !value)}><Icon name={visible ? 'eyeOff' : 'eye'} size={20} /></button> : null}
    </div>
    {hint ? <small id={`${name}-hint`}>{hint}</small> : null}
    {error ? <small className="form-error-text" id={`${name}-error`}>{error}</small> : null}
  </div>
}
