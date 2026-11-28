import { useState } from 'react'
import './registration.css'

export default function Registration() {
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
        setStatus('created')
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
        <a href="/">B2B-маркетплейс ИТ-услуг</a>
      </header>
      <main className="registration-main">
        <div className="registration-intro">
          <h1>Регистрация</h1>
          {status !== 'created' && (
            <p>Выберите, как будете работать на площадке — размещать задания или выполнять их.</p>
          )}
        </div>

        <div className="registration-panel">
          <div role="status">
            {status === 'created' && <h2>Аккаунт создан</h2>}
          </div>
          {status !== 'created' && (
            <form onSubmit={register} aria-label="Регистрация" aria-busy={submitting}>
              <fieldset className="registration-fields" disabled={submitting}>
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

                <fieldset className="registration-roles">
                  <legend>Роль</legend>
                  <div className="registration-role-options">
                    <label><input type="radio" name="role" value="CUSTOMER" required /> Заказчик</label>
                    <label><input type="radio" name="role" value="EXECUTOR" required /> Исполнитель</label>
                  </div>
                </fieldset>

                {formError && <p className="registration-error" role="alert">{formError}</p>}
                <button type="submit">{submitting ? 'Создаём аккаунт…' : 'Создать аккаунт'}</button>
              </fieldset>
            </form>
          )}
        </div>
      </main>
    </div>
  )
}
