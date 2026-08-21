# Bitbucket pull requests comments statistics

[![Java CI with Maven](https://github.com/andrei-punko/bitbucket-pullrequests-comments-statistics/actions/workflows/maven.yml/badge.svg)](https://github.com/andrei-punko/bitbucket-pullrequests-comments-statistics/actions/workflows/maven.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

CLI-утилита собирает статистику по комментариям в pull requests Bitbucket репозитория и сохраняет её в CSV.

Приложение **только читает** Bitbucket API (`GET`). Ничего в репозитории, PR и комментариях не меняется. На диск пишется только локальный CSV.

## Требования

- JDK 21
- Maven 3
- Учётная запись Bitbucket с доступом к репозиторию

## Настройка

Скопируйте пример окружения и заполните значения:

```bat
copy .env.example .env
```

Приложение берёт настройки **только из `.env`**:

| Переменная | Описание |
|---|---|
| `REPOSITORY_URL` | API URL репозитория, например `https://api.bitbucket.org/2.0/repositories/workspace/repo/` |
| `EMAIL` | email аккаунта Atlassian |
| `TOKEN` | API-токен Bitbucket |
| `CSV_PATH_TO_EXPORT` | путь к CSV-файлу отчёта |

Файл `.env` не коммитится.

### Как получить токен

1. Откройте [API tokens](https://id.atlassian.com/manage-profile/security/api-tokens) в настройках аккаунта Atlassian.
2. Создайте **API token with scopes**, выберите приложение Bitbucket.
3. Выдайте только чтение:
   - `read:repository:bitbucket`
   - `read:pullrequest:bitbucket`
4. В `.env` укажите email аккаунта в `EMAIL` и сам токен в `TOKEN`.

Обычный пароль аккаунта Bitbucket для REST API не подходит. Если API отвечает `401 Unauthorized`, проверьте email и scopes токена.

## Запуск

Из каталога модуля:

```bat
run.bat
```

Скрипт выполняет `mvn compile exec:java`. При старте приложение читает `.env` из текущего каталога и использует `EMAIL`/`TOKEN` для Basic Auth.

Либо вручную:

```bat
mvn compile exec:java
```

## Результат

В файл из `CSV_PATH_TO_EXPORT` пишется отчёт по закрытым PR (не `OPEN`): id, даты, ссылка, дни без активности, теги в комментариях (`#bug`, `#style`, …) и число коммитов.
