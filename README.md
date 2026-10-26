# Книга відгуків

Навчальний вебдодаток Jakarta Servlet: сторінка з формою, JSON API та збереження коментарів у файловій базі H2.

## Середовище і запуск

- Для цієї збірки використано JetBrains Runtime JDK 25.0.4 та Maven 3.9.16 із IntelliJ IDEA 2026.2.2. Код компілюється з `release 21`, тому підійде також JDK 21.
- У корені проєкту: `mvn clean package`, потім `mvn jetty:run`.
- Сторінка: http://localhost:8080/
- Зупинка сервера: `Ctrl+C` у терміналі з Maven.

Якщо `mvn` не додано до PATH, в IntelliJ IDEA можна запустити Maven goal `jetty:run` з панелі Maven.

## API

| Метод і шлях | Результат |
| --- | --- |
| `GET /` | HTML-сторінка з формою та списком відгуків |
| `GET /comments` | `200 OK`, JSON-масив від найновішого запису |
| `POST /comments` | Поля форми `author` і `text`; `204 No Content` після збереження |

У `POST` поле `author` обов'язкове і має не більш як 64 символи, `text` обов'язкове і має не більш як 1000 символів. Порожні та надто довгі значення повертають `400 Bad Request`, помилка БД — `500 Internal Server Error`.

## База даних і журнал

JDBC URL: `jdbc:h2:file:./data/guest;AUTO_SERVER=TRUE`. Файл бази створюється відносно каталогу запуску Maven: `data/guest.mv.db`. Під час старту `AppInit` створює каталог `data` і таблицю `comments`, якщо їх ще немає. Після успішного додавання `CommentsServlet` записує один рядок рівня `INFO` у форматі `New comment id=... author=... length=...`.
