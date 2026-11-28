// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import App from './App.jsx'

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  window.history.replaceState(null, '', '/')
})

function openRegistration(request) {
  vi.stubGlobal('fetch', request)
  window.history.replaceState(null, '', '/register')
  render(<App />)
}

function fillForm(role) {
  fireEvent.change(screen.getByLabelText('Имя'), { target: { value: 'Анна Смирнова' } })
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'anna@example.test' } })
  fireEvent.change(screen.getByLabelText('Пароль'), { target: { value: 'password123' } })
  fireEvent.click(screen.getByRole('radio', { name: new RegExp(role) }))
}

it.each([
  ['Заказчик', 'CUSTOMER'],
  ['Исполнитель', 'EXECUTOR'],
])('регистрирует участника с ролью %s', async (role, value) => {
  const request = vi.fn().mockResolvedValue(new Response(null, { status: 201 }))
  openRegistration(request)
  fillForm(role)

  fireEvent.click(screen.getByRole('button', { name: 'Создать аккаунт' }))

  expect(request).toHaveBeenCalledWith('/api/v1/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: expect.any(String),
  })
  expect(JSON.parse(request.mock.calls[0][1].body)).toEqual({
    fullName: 'Анна Смирнова',
    email: 'anna@example.test',
    password: 'password123',
    role: value,
  })
  expect(await screen.findByText('Аккаунт создан')).toBeTruthy()
  expect(screen.getByText('anna@example.test')).toBeTruthy()
  expect(screen.getByText(role)).toBeTruthy()
  expect(screen.getByRole('link', { name: 'На главную' }).getAttribute('href')).toBe('/')
  expect(screen.queryByLabelText('Пароль')).toBeNull()
})

it('сохраняет заполненную форму при конфликте и позволяет исправить email', async () => {
  const request = vi.fn()
    .mockResolvedValueOnce(new Response(null, { status: 409 }))
    .mockResolvedValueOnce(new Response(null, { status: 201 }))
  openRegistration(request)
  fillForm('Исполнитель')

  fireEvent.click(screen.getByRole('button', { name: 'Создать аккаунт' }))

  const error = await screen.findByRole('alert')
  const email = screen.getByLabelText('Email')
  expect(error.textContent).toBe('Этот email уже зарегистрирован.')
  expect(email.getAttribute('aria-invalid')).toBe('true')
  expect(email.getAttribute('aria-describedby')).toBe(error.id)
  expect(email.value).toBe('anna@example.test')
  expect(screen.getByLabelText('Имя').value).toBe('Анна Смирнова')
  expect(screen.getByLabelText('Пароль').value).toBe('password123')
  expect(screen.getByRole('radio', { name: /Исполнитель/ }).checked).toBe(true)

  fireEvent.change(email, { target: { value: 'anna.new@example.test' } })
  expect(screen.queryByRole('alert')).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: 'Создать аккаунт' }))
  expect(await screen.findByText('Аккаунт создан')).toBeTruthy()
})

it.each([
  [() => Promise.resolve(new Response(null, { status: 400 })), 'Проверьте данные и попробуйте ещё раз.'],
  [() => Promise.resolve(new Response(null, { status: 500 })), 'Не удалось создать аккаунт. Попробуйте ещё раз.'],
  [() => Promise.reject(new TypeError()), 'Не удалось связаться с сервером. Попробуйте ещё раз.'],
])('сохраняет форму, когда отправка завершается ошибкой', async (request, message) => {
  openRegistration(request)
  fillForm('Заказчик')
  fireEvent.click(screen.getByRole('button', { name: 'Создать аккаунт' }))

  expect((await screen.findByRole('alert')).textContent).toBe(message)
  expect(screen.getByLabelText('Пароль').value).toBe('password123')
  expect(screen.getByRole('button', { name: 'Создать аккаунт' }).disabled).toBe(false)
})

it('отключает отправку, пока запрос выполняется', () => {
  const request = vi.fn().mockImplementation(() => new Promise(() => {}))
  openRegistration(request)
  fillForm('Заказчик')

  fireEvent.click(screen.getByRole('button', { name: 'Создать аккаунт' }))
  const button = screen.getByRole('button', { name: 'Создаём аккаунт…' })
  expect(button.matches(':disabled')).toBe(true)
  fireEvent.click(button)
  expect(request).toHaveBeenCalledTimes(1)
})
