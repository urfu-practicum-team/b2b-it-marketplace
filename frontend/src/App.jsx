import { useEffect, useState } from 'react'
import Registration from './Registration.jsx'

export default function App() {
  return ['/register', '/register/'].includes(window.location.pathname) ? <Registration /> : <HealthPage />
}

function HealthPage() {
  const [message, setMessage] = useState('Проверяем состояние сервера…')

  useEffect(() => {
    const controller = new AbortController()

    async function checkHealth() {
      try {
        const response = await fetch('/api/v1/health', { signal: controller.signal })
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`)
        }

        const { status } = await response.json()
        setMessage(status === 'UP' ? 'Сервер работает (UP)' : 'Сервер сообщил о сбое')
      } catch {
        if (!controller.signal.aborted) {
          setMessage('Не удалось проверить состояние сервера. Обновите страницу, чтобы повторить запрос.')
        }
      }
    }

    checkHealth()
    return () => controller.abort()
  }, [])

  return (
    <main className="health-page">
      <h1>B2B-маркетплейс ИТ-услуг</h1>
      <p role="status">{message}</p>
      <a href="/register">Зарегистрироваться</a>
    </main>
  )
}
