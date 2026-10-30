# Лабораторна робота 2 — Трирівнева архітектура

Виконав: Самонов Михайло. Каталог книг і відгуки на Maven, Servlet API, Jetty 11 та JDBC/H2.

## Збірка і запуск

Потрібні JDK 17+ і Maven 3.9+. У папці `lab2`:

```shell
mvn clean install
mvn -f web/pom.xml jetty:run
```

У Windows можна запустити `./run.ps1`. Скрипт також знаходить Maven та JDK установленої IntelliJ IDEA 2026.2.2, якщо Maven відсутній у PATH.
Відкрити http://localhost:8081/books. Зупинка: Ctrl+C у консолі сервера.
У IntelliJ IDEA відкрити `lab2/pom.xml` як Maven-проєкт. Maven goal `clean install` виконати для parent, а `jetty:run` — для web.

Код компілюється з `release 17`. Перевірено на JetBrains Runtime 25.0.4, Maven 3.9.16, Jetty 11.0.25.
Тільки перевірка: `mvn clean verify`. У кожному модулі є ArchUnit-тести; HTTP-тести запускають справжній embedded Jetty на випадковому вільному порту.

## Межі модулів

* `core` — Book, Comment, Page, PageRequest, порти та сервіси. Немає production-залежностей.
* `persistence` → `core` — JDBC-репозиторії, H2 та схема.
* `web` → `core` — HTTP, HTML, JSON, журнал. `persistence` підключено зі scope `runtime` лише для пакування і завантаження адаптера.

`AppInit` знаходить `RepositoryProvider` через стандартний `ServiceLoader`. Файл `META-INF/services/ua.edu.sumdu.core.port.RepositoryProvider` міститься у persistence. Жоден production-клас web не імпортує persistence/JDBC/H2; ArchUnit перевіряє це без винятків для конфігурації.

## HTTP

| Метод | Шлях | Призначення |
|---|---|---|
| GET | `/books?q=&page=0&size=10&sort=id,asc` | Каталог HTML; з `Accept: application/json` — JSON Page |
| GET | `/books/{id}` | Сторінка книги; з JSON Accept — Book |
| POST | `/books` | Додати книгу: title, author, pubYear |
| GET | `/books/{id}/comments` | Сторінка відгуків JSON: page, size, sort, author, since |
| POST | `/books/{id}/comments` | Додати відгук: author, text; 201, Location, JSON |
| DELETE | `/books/{id}/comments/{commentId}` | Видалити свіжий відгук; 204 |

Для кожного `/books...` є JSON-варіант `/api/books...`, незалежний від Accept. POST приймає JSON або application/x-www-form-urlencoded. HTML і JSON мають UTF-8.

`page` нумерується з 0, `size`: 1–100. Для книг дозволено `id`, `title`, `author`, `pubYear` з напрямком `asc`/`desc`, наприклад `sort=title,asc`. Для відгуків — `id`, `author`, `createdAt`. Друге сортування за id забезпечує стабільні сторінки. Пошук назви/автора не враховує регістр. `%` та `_` у пошуковому рядку трактуються буквально, а параметри передаються в PreparedStatement. `total` враховує ті самі фільтри, що й items. Порожній результат — 200 і порожній items.

Приклад фільтра відгуків: `/api/books/1/comments?author=Михайло&since=2026-10-01T00:00:00Z&page=0&size=10&sort=createdAt,desc`.

## Бізнес-правила та помилки

`CatalogService`: назва й автор книги обов'язкові, до 255 символів; рік 1–9999.
`CommentService`: автор 1–64, текст 1–1000 символів після trim. Книга має існувати. Дата створення призначається серверним Clock.
Видалення дозволене, коли `createdAt <= now < createdAt + 24 години`. Рівно через 24 години повертається 409. Час береться зі збереженого відгуку за парою bookId/commentId. Передавати власний createdAt для видалення неможливо. Clock інжектується для точних граничних тестів.

400 — невалідні поля/параметри/JSON; 404 — відсутня книга/відгук/маршрут; 409 — минув строк видалення; 405 — метод не підтримується; 500 — неочікувана помилка сховища/сервера.

```json
{"error":{"status":400,"code":"BAD_REQUEST","message":"size має бути від 1 до 100","path":"/books","timestamp":"2026-10-29T20:00:00Z","requestId":"..."}}
```

`X-Request-Id` пов'язує відповідь із журналом. Внутрішні винятки та SQL не потрапляють у 500-відповідь. INFO — створення/видалення відгуку з ключовими полями, WARN — 4xx, ERROR зі стеком — 5xx. Повний текст відгуку не записується в лог. Журнал: `logs/catalog.log` відносно робочої папки Maven.

## Дані

Типово використовується `jdbc:h2:file:./data/catalog;AUTO_SERVER=TRUE` (користувач sa, порожній пароль для локальної навчальної БД). Відносні data/logs визначаються робочою папкою процесу. Ініціалізація не видаляє таблиці: CREATE IF NOT EXISTS і початкові шість книг додаються без дублікатів. FK зв'язує коментарі з книгами, час зберігається як TIMESTAMP(9) WITH TIME ZONE.
URL можна змінити JVM-властивістю `-Dcatalog.db.url=...`.

Тести працюють з окремими in-memory/тимчасовими файловими БД; дані запущеного каталогу вони не змінюють. Штучна відмова сховища для 500 реалізована тільки у HTTP-тесті, у застосунку немає спеціального маршруту для помилок.

## Джерела

Методичні вказівки до лабораторної роботи 2, СумДУ, 2026–2027; вихідний звіт лабораторної роботи 1.
[Jetty Maven Plugin 11](https://jetty.org/docs/jetty/11/programming-guide/maven-jetty/jetty-maven-plugin.html), [ArchUnit User Guide](https://www.archunit.org/userguide/html/000_Index.html).
