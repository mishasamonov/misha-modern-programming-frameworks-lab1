# Лабораторна робота 3 — Javalin

Самонов Михайло. Каталог книг і відгуків. Гілка `lab3-javalin`.
Проєкт продовжує лабораторну 2; назву каталогу `lab2` збережено для відкритого проєкту IntelliJ IDEA.

## Запуск

Відкрити `pom.xml` в IntelliJ IDEA як Maven-проєкт. Потрібні JDK 17+ і Maven 3.9+.

```shell
mvn clean install
mvn -f web/pom.xml exec:java
```

У Windows `./run.ps1` знаходить Maven/JDK із PATH або встановленої IntelliJ IDEA.
Можна запустити `sumdu.edu.ua.web.JavalinBookApp.main()` кнопкою біля main в IDEA.
Адреса: http://localhost:8080/api/books. Зупинка: Stop в IDEA або Ctrl+C.
Версії з методички: Javalin 6.2.0, Jackson 2.17.2, exec-maven-plugin 3.5.0; release 17.

## Структура

`core` і `persistence` повністю збережені з лабораторної 2. У `web` пакування змінено з WAR на JAR.
Web викликає сервіси та порти core; JDBC-адаптер підключено через ServiceLoader та runtime-залежність.
`web/reference/servlet-api` містить вихідну реалізацію для порівняння; вона не компілюється й не запускається.

## REST API

| Метод | Шлях | Результат |
|---|---|---|
| GET | `/` | Book catalogue — Javalin 6.2.0 |
| GET | `/api/books?q=Java&page=0&size=1&sort=title,asc` | 200, JSON Page |
| GET | `/api/books/{id}` | 200, JSON Book |
| POST | `/api/books` | 201, JSON Book, Location |
| GET | `/api/books/{id}/comments` | 200, JSON Page; author, since, page, size, sort |
| POST | `/api/books/{id}/comments` | 201, JSON Comment, Location |
| POST | `/api/comments` | 201; варіант методички з bookId у JSON |
| DELETE | `/api/books/{id}/comments/{commentId}` | 204, порожнє тіло |

POST приймає application/json. Книга: title, author, pubYear. Відгук: author, text; для /api/comments також bookId.
page — від 0, size — 1–100. Збережено пошук, пагінацію, сортування, фільтри та правило видалення протягом 24 годин.

app.before() записує метод і шлях, призначає X-Request-Id. app.after() фіксує статус.
app.exception() формує JSON-помилки: 400 — параметри/JSON, 404 — ресурс, 409 — строк видалення, 415 — Content-Type, 500 — збій.
Непідтримуваний метод: 405 і Allow. Журнал: logs/catalog.log відносно робочої папки Java-процесу.

## Перевірка

40 тестів: core — 15, persistence — 8, web — 17 (12 HTTP + 5 ArchUnit).
HTTP-тести запускають справжній Javalin/Jetty на випадковому порту й окремій H2 in-memory.
Перевірено UTF-8, пагінацію, створення книги, обидва POST-маршрути відгуків, DELETE, 24 години, коди 400/404/405/409/415/500.

Відкрити web/requests/lab3.http у HTTP Client IntelliJ IDEA та натиснути Run All Requests.
12 запитів передають створені ID у DELETE та прибирають свої тестові відгуки.
Навмисно неповний JSON у запиті 10 потрібен для перевірки 400; підсвітка цього фрагмента очікувана.

Файлова H2: jdbc:h2:file:./data/catalog;AUTO_SERVER=TRUE. Робоча папка визначає data/logs.
JVM-властивості: catalog.db.url — URL бази; catalog.port — порт (типово 8080).

## Джерела

Методичка СумДУ до лабораторної 3 (надана PDF), звіт і проєкт лабораторної 2.
[Javalin 6.2.0](https://github.com/javalin/javalin/tree/javalin-parent-6.2.0).
