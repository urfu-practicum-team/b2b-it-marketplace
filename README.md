# B2B-маркетплейс ИТ-услуг с верификацией и эскроу

Командный учебный проект магистратуры УрФУ. Маркетплейс позволит компаниям публиковать задания и находить исполнителей с подтверждённой квалификацией. Оплата резервируется до приёмки результата.

Стек проекта — Java/Spring, React и PostgreSQL.

## Локальная сборка и запуск

Команды выполняются из корня репозитория. Для фронтенда нужен Node.js 22.12+
с npm. В Windows PowerShell можно использовать `npm.cmd` вместо `npm`.

### Сервер Spring Boot

При запущенном Docker выполните:

```sh
docker build -t b2b-it-marketplace-backend ./backend
docker run --rm -p 127.0.0.1:8080:8080 b2b-it-marketplace-backend
```

Для запуска напрямую понадобятся JDK 21 и Maven 3.9:

```sh
mvn -f backend/pom.xml package
java -jar backend/target/marketplace.jar --server.address=127.0.0.1
```

### Клиент React

Оставьте сервер запущенным и в другом терминале выполните:

```sh
npm --prefix frontend ci
npm --prefix frontend run build
npm --prefix frontend run preview -- --host 127.0.0.1 --port 4173 --strictPort
```

Готовая статика сохраняется в `frontend/dist/`. Откройте
<http://127.0.0.1:4173/>. Страница запрашивает `GET /api/v1/health`
через прокси Vite и при ответе `{"status":"UP"}` показывает «Сервер работает (UP)».
