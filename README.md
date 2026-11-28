# B2B-маркетплейс ИТ-услуг с верификацией и эскроу

Командный учебный проект магистратуры УрФУ. Маркетплейс позволит компаниям публиковать задания и находить исполнителей с подтверждённой квалификацией. Оплата резервируется до приёмки результата.

Стек проекта — Java/Spring, React и PostgreSQL.

## Локальная сборка и запуск

Для запуска всего приложения нужен Docker Desktop в режиме Linux-контейнеров с Docker Compose 2.24.4 или новее. Выполняйте команды из корня репозитория.

### Разовая настройка на новом компьютере

Перед первым запуском выполните этот блок в PowerShell с установленным Python 3. Он создаст два файла в каталоге `secrets/`, который исключён из Git. Пароль `postgres` нужен для администрирования, пароль `marketplace_app` — для подключения к базе `marketplace`.

```powershell
New-Item -ItemType Directory -Force secrets | Out-Null
New-Item -ItemType Directory -Force data/portfolio | Out-Null
foreach ($name in 'postgres_password', 'marketplace_app_password') {
    $path = Join-Path $PWD "secrets/$name"
    if (!(Test-Path -LiteralPath $path)) {
        $password = python -c "import secrets; print(secrets.token_hex(32))"
        if ($LASTEXITCODE -ne 0) { throw 'Ошибка генерации пароля' }
        [IO.File]::WriteAllText($path, $password)
    }
}
```

### Запуск всего приложения

Spring сохраняет файлы портфолио в каталоге `/var/lib/marketplace/portfolio`, заданном через `MARKETPLACE_PORTFOLIO_DIRECTORY`. На хосте ему соответствует `./data/portfolio` относительно `compose.yaml`. При запуске на Linux подготовьте каталог командой `sudo install -d -o 10001 -g 10001 -m 0750 ./data/portfolio`, чтобы пользователь Spring мог записывать файлы.

После разовой настройки поднимайте приложение одной командой:

```powershell
docker compose up -d --build --wait
```

Docker собирает React и Spring из текущего кода. Nginx отдаёт страницу и проксирует запросы к Spring, а PostgreSQL хранит данные в именованном томе. Откройте <http://localhost:8081/>. Когда страница получит ответ от API, она покажет «Сервер работает (UP)». Ответ API можно посмотреть по адресу <http://localhost:8081/api/v1/health>.

Для остановки выполните `docker compose stop`. При следующем запуске пароли и данные базы сохранятся.

Процесс автоматической выкладки описан в [документации стенда](docs/deployment.md).
