// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import App from './App.jsx'

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

it.each([
  ['UP', 'Сервер работает (UP)'],
  ['DOWN', 'Сервер сообщил о сбое'],
])('показывает состояние сервера %s', async (status, message) => {
  const request = vi.fn().mockResolvedValue(Response.json({ status }))
  vi.stubGlobal('fetch', request)
  render(<App />)

  expect(screen.getByRole('status').textContent).toBe('Проверяем состояние сервера…')
  expect(request).toHaveBeenCalledWith('/api/v1/health', { signal: expect.any(AbortSignal) })
  expect(await screen.findByText(message)).toBe(screen.getByRole('status'))
})

it.each([
  ['ошибке HTTP', () => Promise.resolve(new Response(null, { status: 503 }))],
  ['сетевой ошибке', () => Promise.reject(new TypeError('Failed to fetch'))],
])('предлагает повторить запрос при %s', async (_, request) => {
  vi.stubGlobal('fetch', request)
  render(<App />)

  expect(await screen.findByText(/Обновите страницу, чтобы повторить запрос/))
    .toBe(screen.getByRole('status'))
})

it('отменяет незавершённый запрос при закрытии страницы', () => {
  const request = vi.fn(() => new Promise(() => {}))
  vi.stubGlobal('fetch', request)
  const { unmount } = render(<App />)
  const [, { signal }] = request.mock.calls[0]

  expect(signal.aborted).toBe(false)
  unmount()
  expect(signal.aborted).toBe(true)
})
