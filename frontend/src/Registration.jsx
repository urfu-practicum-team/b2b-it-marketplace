import { useState } from 'react'
import './registration.css'

export default function Registration() {
  const [account, setAccount] = useState(null)
  const [status, setStatus] = useState('idle')
  const [formError, setFormError] = useState('')
  const [emailError, setEmailError] = useState('')

  async function register(event) {
    event.preventDefault()
    if (status === 'submitting') return

    const fields = Object.fromEntries(new FormData(event.currentTarget))
    setStatus('submitting')
    setEmailError('')
    setFormError('')

    try {
      const response = await fetch('/api/v1/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(fields),
      })

      if (response.status === 201) {
        setAccount({ email: fields.email, role: fields.role })
        return
      }

      if (response.status === 409) {
        setEmailError('Этот email уже зарегистрирован.')
      } else if (response.status === 400) {
        setFormError('Проверьте данные и попробуйте ещё раз.')
      } else {
        setFormError('Не удалось создать аккаунт. Попробуйте ещё раз.')
      }
    } catch {
      setFormError('Не удалось связаться с сервером. Попробуйте ещё раз.')
    }
    setStatus('idle')
  }

  const submitting = status === 'submitting'

  return (
    <div className="registration">
      <header className="registration-header">
        <a href="/">IT Подряд</a>
      </header>
      {account ? (
        <main className="registration-success">
          <span className="success-mark" aria-hidden="true" />
          <h1 tabIndex={-1} ref={node => node?.focus()}>Аккаунт создан</h1>
          <dl>
            <div><dt>Email</dt><dd>{account.email}</dd></div>
            <div><dt>Тип аккаунта</dt><dd>{account.role === 'CUSTOMER' ? 'Заказчик' : 'Исполнитель'}</dd></div>
          </dl>
          <a href="/">На главную</a>
        </main>
      ) : (
        <main className="registration-main">
          <div className="registration-intro">
            <h1>Регистрация</h1>
            <p>Создайте аккаунт для работы с ИТ-заказами.</p>
          </div>
          <div className="registration-panel">
            <form onSubmit={register} aria-label="Регистрация" aria-busy={submitting}>
              <fieldset className="registration-fields" disabled={submitting}>
                <fieldset className="registration-roles">
                  <legend>Тип аккаунта</legend>
                  <div className="registration-role-options">
                    <label>
                      <input type="radio" name="role" value="CUSTOMER" required />
                      <span>Заказчик<small>Размещаю задания</small></span>
                    </label>
                    <label>
                      <input type="radio" name="role" value="EXECUTOR" required />
                      <span>Исполнитель<small>Беру задания в работу</small></span>
                    </label>
                  </div>
                </fieldset>
                <div className="registration-field">
                  <label htmlFor="full-name">Имя</label>
                  <input id="full-name" name="fullName" autoComplete="name" required maxLength={200} />
                </div>
                <div className="registration-field">
                  <label htmlFor="registration-email">Email</label>
                  <input
                    id="registration-email"
                    name="email"
                    type="email"
                    autoComplete="email"
                    required
                    maxLength={255}
                    aria-invalid={emailError ? true : undefined}
                    aria-describedby={emailError ? 'email-error' : undefined}
                    onChange={() => setEmailError('')}
                  />
                  {emailError && <p id="email-error" className="registration-error" role="alert">{emailError}</p>}
                </div>
                <div className="registration-field">
                  <label htmlFor="registration-password">Пароль</label>
                  <input
                    id="registration-password"
                    name="password"
                    type="password"
                    autoComplete="new-password"
                    required
                    minLength={8}
                    maxLength={72}
                    aria-describedby="password-length"
                  />
                  <p id="password-length" className="registration-hint">От 8 до 72 символов</p>
                </div>

                {formError && <p className="registration-error" role="alert">{formError}</p>}
                <button type="submit">{submitting ? 'Создаём аккаунт…' : 'Создать аккаунт'}</button>
              </fieldset>
            </form>
          </div>
        </main>
      )}
    </div>
  )
}
