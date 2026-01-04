# Налаштування Firebase для авторизації

## Крок 1: Створення проекту в Firebase Console

1. Перейдіть на https://console.firebase.google.com/
2. Натисніть "Додати проект" (Add project)
3. Введіть назву проекту (наприклад, "Lab4Stability")
4. Виберіть або створіть Google Analytics акаунт (опціонально)
5. Натисніть "Створити проект"

## Крок 2: Додавання Android додатку

1. В Firebase Console натисніть на іконку Android
2. Введіть:
   - **Package name**: `dev.matsyshyn.lab4` (з AndroidManifest.xml)
   - **App nickname**: Lab4Stability (опціонально)
   - **Debug signing certificate SHA-1**: (опціонально, для тестування)
3. Натисніть "Зареєструвати додаток"

## Крок 3: Завантаження google-services.json

1. Завантажте файл `google-services.json`
2. Скопіюйте його в папку `app/` (на рівні з `build.gradle.kts`)
3. Структура має бути:
   ```
   app/
   ├── google-services.json  ← ТУТ
   ├── build.gradle.kts
   └── src/
   ```

## Крок 4: Увімкнення Email/Password авторизації

1. В Firebase Console перейдіть в **Authentication** → **Sign-in method**
2. Натисніть на **Email/Password**
3. Увімкніть перемикач "Enable"
4. Натисніть "Save"

## Крок 5: Перевірка

Після виконання всіх кроків:
- Запустіть додаток
- Ви побачите екран авторизації
- Можете зареєструватися або увійти

## Важливо!

- Файл `google-services.json` містить конфіденційну інформацію
- НЕ додавайте його в Git (додайте в `.gitignore`)
- Кожен розробник має мати свій файл з Firebase Console

