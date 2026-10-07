# Сборка APK через GitHub Actions

Проект уже содержит workflow `.github/workflows/build-apk.yml`.

## Вариант 1 — GitHub Web

1. Создайте новый **private repository** на GitHub.
2. Распакуйте этот ZIP.
3. Загрузите содержимое проекта в репозиторий.
4. Откройте вкладку **Actions**.
5. Выберите **Build NotificationLog APK**.
6. Нажмите **Run workflow**.
7. После завершения откройте успешный запуск workflow.
8. Внизу страницы скачайте artifact **NotificationLog-debug-apk**.
9. Внутри будет `app-debug.apk`.

Workflow также запускается автоматически при push в `main` или `master`.

## Важно

Это debug APK. Для личной установки на Xiaomi он подходит. Для публикации в Google Play потребуется отдельная release-подпись.

## Приватность

Исходный код приложения не содержит `INTERNET` permission. GitHub Actions используется только для сборки исходников; само приложение сеть не использует.
