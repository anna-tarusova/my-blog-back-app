# my-blog-back-app

Бэкенд приложения-блога с использованием Spring Framework, модульных и интеграционных тестов.

## Стек

- Java 21, Maven
- Spring Framework 6.1: контейнер IoC, Spring MVC, Spring Test
- Spring Data JDBC 3.3 + Spring JDBC, пул соединений HikariCP
- PostgreSQL (прод), H2 in-memory (тесты)
- Встроенный Tomcat (tomcat-embed-core), JSON через Jackson
- JUnit 5, MockMvc, Mockito, Lombok, SLF4J + Logback

## Слои приложения

| Слой | Пакет | Классы |
| --- | --- | --- |
| Controller | `ru.practicum.controller` | `PostController` (+ `ru.practicum.web.ApiExceptionHandler`) |
| Service | `ru.practicum.service` | `PostService`, `PostServiceImpl`, `PostMapper` |
| DAO | `ru.practicum.dao` | `PostRepository` (CRUD + fragment `PostDao`, реализация `PostDaoImpl`), `TagRepository` |
| Model | `ru.practicum.model` | `Post`, `Comment`, `Tag`, `Page` (значимый объект пагинации) |
| DTO | `ru.practicum.dto` | `PostPreview` (чтение из БД), `PostCreateDto`, `PostUpdateDto` (запросы), `PostDto`, `PostsPageDto`, `CommentDto`, `PostImageDto` (ответы API) |
| Config | `ru.practicum.config` | `AppConfig`, `WebConfig`, `JdbcConfig`, `WebAppInitializer` |

## Эндпоинты

### Лента постов

```
GET /api/posts?search=Lalala&pageNumber=1&pageSize=5
```

Все три параметра обязательны; `pageNumber` и `pageSize` должны быть больше 0.
Некорректные или отсутствующие параметры — `400`, неизвестный путь — `404`.

```json
{
  "posts": [
    {
      "id": 1,
      "title": "Название поста 1",
      "text": "Текст поста в формате Markdown...",
      "tags": ["tag_1", "tag_2"],
      "likesCount": 5,
      "commentsCount": 1
    }
  ],
  "hasPrev": true,
  "hasNext": false,
  "lastPage": 3
}
```

Правила формирования ответа:

- посты отсортированы от новых к старым (по `id` по убыванию);
- `search` — подстрока названия поста, поиск без учёта регистра (`LOWER(title) LIKE LOWER('%search%')`);
- текст в ленте обрезается до 128 символов с добавлением `…`;
- теги отдаются без символа `#`;
- `likesCount` и `commentsCount` считаются в SQL по таблицам `likes` и `comments`;
- `hasPrev` — страница не первая, `hasNext` — страница не последняя, `lastPage` — номер последней страницы (минимум 1);
- пустой результат поиска — это `posts: []`, `lastPage: 1`.

### Создание поста

```
POST /api/posts
Content-Type: application/json

{
  "title": "Название поста 3",
  "text": "Текст поста в формате Markdown...",
  "tags": ["tag_1", "tag_2"]
}
```

Ответ — `201 Created` и созданный пост:

```json
{
  "id": 3,
  "title": "Название поста 3",
  "text": "Текст поста в формате Markdown...",
  "tags": ["tag_1", "tag_2"],
  "likesCount": 0,
  "commentsCount": 0
}
```

Правила:

- все три поля обязательны: пустые или отсутствующие `title`/`text` и отсутствующий `tags` дают `400` (невалидный JSON — тоже `400`);
- `title` и `text` обрезаются по краям, текст созданного поста отдаётся **целиком** (обрезка до 128 символов действует только в ленте);
- теги нормализуются: снимается `#`, убираются пробелы по краям, пустые значения и дубликаты (поэтому повторяющиеся теги не нарушают `UNIQUE (post_id, name)`);
- пост и его теги сохраняются в одной транзакции;
- `likesCount` и `commentsCount` у нового поста всегда `0`.

### Редактирование поста

```
PUT /api/posts/3
Content-Type: application/json

{
  "id": 3,
  "title": "Название поста 3",
  "text": "Текст поста в формате Markdown...",
  "tags": ["tag_1", "tag_2"]
}
```

Ответ — `200 OK` и обновлённый пост:

```json
{
  "id": 3,
  "title": "Название поста 3",
  "text": "Текст поста в формате Markdown...",
  "tags": ["tag_1", "tag_2"],
  "likesCount": 5,
  "commentsCount": 1
}
```

Правила:

- все четыре поля обязательны: пустые/отсутствующие `id`/`title`/`text`, отсутствующий `tags`
  и несовпадающий с путём `id` дают `400` (невалидный JSON — тоже `400`);
- `title` и `text` обрезаются по краям, текст отдаётся целиком (обрезка до 128 символов — только в ленте);
- теги нормализуются так же, как при создании, и **заменяют** прежний список тегов поста целиком;
- `likesCount` и `commentsCount` — фактические числа лайков и комментариев поста;
- неизвестный `id` даёт `404`;
- обновление поста, тегов и подсчёт счётчиков выполняются в одной транзакции.

### Удаление поста

```
DELETE /api/posts/1
```

Правила:

- успешное удаление — `200 OK` без тела;
- вместе с постом удаляются все его комментарии, а также (каскадом по внешним ключам
  `ON DELETE CASCADE`) теги, лайки и картинка поста;
- неизвестный `id` — `404`, нечисловой `id` в пути — `400`.

### Инкремент лайков

```
POST /api/posts/1/likes
```

Правила:

- успешный ответ — `200 OK`, в теле — обновлённое число лайков поста (JSON-число, например `1`);
- лайк добавляется в таблицу `likes`, возвращается фактическое `COUNT(*)` по посту,
  поэтому в ленте `likesCount` сразу отражает новый счётчик;
- неизвестный `id` — `404`, нечисловой `id` в пути — `400`.

### Обновление картинки поста

```
PUT /api/posts/1/image
Content-Type: multipart/form-data; boundary=...

--boundary
Content-Disposition: form-data; name="image"; filename="image_name.jpg"
Content-Type: image/jpeg

<байты файла>
--boundary--
```

Правила:

- часть запроса называется `image` (её имя задаёт `Content-Disposition: form-data; name="image"`),
  имя файла берётся из `filename` (обрезается по краям);
- успешное обновление — `200 OK` без тела; одна картинка на пост: повторная загрузка
  полностью заменяет прежнюю (upsert в таблицу `post_images`);
- картинка хранится в БД в колонке `data` (PostgreSQL `bytea`), вместе с `file_name` и `content_type`;
- MIME-тип берётся из multipart-части; пустой заменяется на `application/octet-stream`;
- отсутствующая часть или пустой файл — `400`, неизвестный пост — `404`,
  запрос без `multipart/form-data` — `415`, файл больше лимита — `413` (см. «multipart-конфигурация»).

### Получение картинки поста

```
GET /api/posts/1/image
```

Правила:

- в теле ответа — **сырые байты** картинки, `Content-Type` — MIME-тип, сохранённый при загрузке
  (некорректное сохранённое значение заменяется на `application/octet-stream`);
- неизвестный `id` или пост без картинки — `404`, нечисловой `id` — `400`.

### Получение комментариев поста

```
GET /api/posts/1/comments
Content-Type: application/json
```

```json
[
  {
    "id": 1,
    "text": "Комментарий к посту 1",
    "postId": 1
  },
  {
    "id": 2,
    "text": "Ещё один комментарий к посту 1",
    "postId": 1
  }
]
```

Правила:

- комментарии отдаются в порядке добавления (по `id` по возрастанию);
- у поста без комментариев — пустой массив `[]`;
- неизвестный `id` — `404`, нечисловой `id` — `400`.

## Схема БД

Схема создаётся при старте приложения из [`schema.sql`](src/main/resources/schema.sql):

- `posts (id, title, text, created_at, updated_at)` — посты;
- `comments (id, post_id, text, created_at, updated_at)` — комментарии, `FK post_id → posts(id) ON DELETE CASCADE`;
- `tags (id, post_id, name)` — теги поста, `UNIQUE (post_id, name)`;
- `likes (id, post_id)` — по одному лайку на строку, `COUNT(*)` даёт количество лайков поста;
- `post_images (post_id, file_name, content_type, data)` — картинка поста (PK и `FK post_id → posts(id) ON DELETE CASCADE`),
  обновляется через `PUT /api/posts/{id}/image`.

## Запуск

```bash
docker compose up -d          # PostgreSQL на localhost:5432 (db/user/password: blog)
mvn test                      # тесты на H2, Postgres не требуется

mvn -q compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "target/classes:$(cat target/classpath.txt)" ru.practicum.Main
curl 'http://localhost:8080/api/posts?search=&pageNumber=1&pageSize=5'
```

Параметры подключения к БД лежат в `src/main/resources/db.properties`
и переопределяются системными свойствами или переменными окружения
(`-Djdbc.url=...`, `JDBC_USERNAME`, `JDBC_PASSWORD`).

## Сборка WAR и запуск в сервлет-контейнере

Пакетирование проекта — `war` (см. `pom.xml`, `maven-war-plugin`):

```bash
mvn clean package        # результат: target/my-blog-back-app-local-1.0-SNAPSHOT.war
```

В war-файл попадают `WEB-INF/web.xml`, классы приложения (`WEB-INF/classes`),
зависимости (`WEB-INF/lib`) и `schema.sql`; `provided`-зависимости
(servlet-api, tomcat-embed, Lombok) исключены.

Интеграция с сервлет-контейнером (Tomcat 10.1 / Servlet 6.0, Jakarta EE 10) описана двумя способами:

- **`src/main/webapp/WEB-INF/web.xml`** — `ContextLoaderListener` поднимает корневой контекст
  (`AppConfig`: сервисы, DAO, PostgreSQL), `DispatcherServlet` — контекст web-слоя (`WebConfig`);
- **`ru.practicum.config.WebAppInitializer`** — программная (Java-based) альтернатива:
  Servlet-контейнер находит её автоматически через `SpringServletContainerInitializer`.
  Если приложение уже развёрнуто с `web.xml`, инициализатор ничего не делает, чтобы контексты
  не поднимались дважды.

### multipart-конфигурация

Загрузка картинки (`PUT /api/posts/{id}/image`) требует поддержки multipart с двух сторон:

- **Resolver**: бин `multipartResolver` (`StandardServletMultipartResolver`) в `WebConfig` —
  DispatcherServlet оборачивает запрос контейнера и читает части через `request.getParts()`;
- **Лимиты контейнера** (иначе `getParts()` бросает исключение): `<multipart-config>` в `web.xml`
  для `DispatcherServlet` либо `WebAppInitializer.customizeRegistration` при программной
  регистрации. Лимиты: файл до 10 МБ, запрос до 15 МБ; значения в обоих местах одинаковы
  и проверяются тестом `ServletContainerConfigTest`.

Развёртывание war в Tomcat:

```bash
cp target/my-blog-back-app-local-1.0-SNAPSHOT.war $CATALINA_HOME/webapps/my-blog.war
# PostgreSQL и настройки подключения — как в разделе «Запуск»
curl 'http://localhost:8080/my-blog/api/posts?search=&pageNumber=1&pageSize=5'
```

Развёртывание проверяется интеграционным тестом `WebappDeploymentTest`
(встроенный Tomcat + webapp-структура с `web.xml`), консистентность
`web.xml` и `WebAppInitializer` — тестом `ServletContainerConfigTest`.

