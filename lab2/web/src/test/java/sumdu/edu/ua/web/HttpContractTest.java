package sumdu.edu.ua.web;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import ua.edu.sumdu.core.domain.*;
import ua.edu.sumdu.core.error.StorageException;
import ua.edu.sumdu.core.port.*;
import ua.edu.sumdu.core.service.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HttpContractTest {
  Javalin server;
  static final ObjectMapper JSON = new ObjectMapper();
  String base;
  final HttpClient client = HttpClient.newHttpClient();
  final Instant start = Instant.parse("2026-10-29T12:00:00Z");
  Instant time = start;

  @BeforeAll
  void start() throws Exception {
    System.setProperty("catalog.db.url", "jdbc:h2:mem:http-tests;DB_CLOSE_DELAY=-1");
    var provider = ServiceLoader.load(RepositoryProvider.class).findFirst().orElseThrow();
    var real = provider.catalog();
    CatalogRepositoryPort failing =
        new CatalogRepositoryPort() {
          public Optional<Book> findById(long id) {
            return real.findById(id);
          }

          public Book add(String t, String a, int y) {
            return real.add(t, a, y);
          }

          public Page<Book> search(String q, PageRequest p) {
            if (q.equals("storage-failure-test"))
              throw new StorageException("Simulated storage failure for HTTP test");
            return real.search(q, p);
          }
        };
    var catalog = new CatalogService(failing);
    Clock clock =
        new Clock() {
          public ZoneId getZone() {
            return ZoneOffset.UTC;
          }

          public Clock withZone(ZoneId z) {
            return this;
          }

          public Instant instant() {
            return time;
          }
        };
    server =
        JavalinBookApp.create(
            catalog, new CommentService(provider.comments(), catalog, clock), clock);
    server.start("127.0.0.1", 0);
    base = "http://127.0.0.1:" + server.port();
  }

  @AfterAll
  void stop() throws Exception {
    server.stop();
    System.clearProperty("catalog.db.url");
  }

  @BeforeEach
  void resetTime() {
    time = start;
  }

  HttpResponse<String> get(String path) throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create(base + path))
            .header("Accept", "application/json")
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  HttpResponse<String> post(String path, String value) throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create(base + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(value))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  HttpResponse<String> delete(long id) throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create(base + "/api/books/1/comments/" + id)).DELETE().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  void error(HttpResponse<String> r, int status) throws Exception {
    assertEquals(status, r.statusCode(), r.body());
    assertTrue(
        r.headers()
            .firstValue("content-type")
            .orElse("")
            .toLowerCase()
            .contains("application/json"));
    var e = JSON.readTree(r.body()).get("error");
    assertEquals(status, e.get("status").asInt());
    assertNotNull(e.get("requestId"));
    assertEquals(r.headers().firstValue("X-Request-Id").orElseThrow(), e.get("requestId").asText());
    assertNotNull(e.get("timestamp"));
  }

  @Test
  void booksSearchPaginationAndUtf8() throws Exception {
    var r = get("/api/books?q=Java&page=0&size=1&sort=title,asc");
    assertEquals(200, r.statusCode());
    var j = JSON.readTree(r.body());
    assertEquals(2, j.get("total").asInt());
    assertEquals(1, j.get("items").size());
    assertEquals(1, j.get("request").get("size").asInt());
    assertTrue(get("/api/books/1").body().contains("Чистий код"));
  }

  @Test
  void invalidParamsAre400() throws Exception {
    for (String p :
        List.of(
            "?page=-1",
            "?page=abc",
            "?size=0",
            "?size=101",
            "?sort=title;DROP",
            "?sort=createdAt,asc")) error(get("/api/books" + p), 400);
    error(get("/api/books/no-number"), 400);
    error(get("/api/books/1/comments?since=no-date"), 400);
  }

  @Test
  void missingResourcesAre404() throws Exception {
    error(get("/api/books/999999"), 404);
    error(get("/unknown"), 404);
    error(get("/api/books/999999/comments"), 404);
    error(delete(999999), 404);
  }

  @Test
  void malformedAndInvalidPostAre400() throws Exception {
    error(post("/api/books/1/comments", "{"), 400);
    error(post("/api/books/1/comments", "{\"author\":\" \",\"text\":\"Text\"}"), 400);
    error(
        post("/api/books/1/comments", "{\"author\":\"A\",\"text\":\"" + "a".repeat(1001) + "\"}"),
        400);
  }

  @Test
  void createsAndDeletesComment() throws Exception {
    var r = post("/api/books/1/comments", "{\"author\":\"Михайло\",\"text\":\"Корисна книга\"}");
    assertEquals(201, r.statusCode(), r.body());
    long id = JSON.readTree(r.body()).get("id").asLong();
    assertTrue(r.headers().firstValue("location").orElseThrow().endsWith("/" + id));
    var deleted = delete(id);
    assertEquals(204, deleted.statusCode());
    assertEquals("", deleted.body());
    error(delete(id), 404);
  }

  @Test
  void deletionAt24HoursIs409AndKeepsComment() throws Exception {
    var r = post("/api/books/1/comments", "{\"author\":\"Михайло\",\"text\":\"Старий відгук\"}");
    long id = JSON.readTree(r.body()).get("id").asLong();
    time = start.plus(Duration.ofHours(24));
    error(delete(id), 409);
    assertTrue(get("/api/books/1/comments").body().contains("Старий відгук"));
  }

  @Test
  void storageFailureIs500WithoutInternalDetails() throws Exception {
    var r = get("/api/books?q=storage-failure-test");
    error(r, 500);
    assertFalse(r.body().contains("Simulated"));
    assertFalse(r.body().contains("StorageException"));
  }

  @Test
  void flatCommentsRouteFromInstructions() throws Exception {
    var r =
        post(
            "/api/comments", "{\"bookId\":2,\"author\":\"Student\",\"text\":\"Interesting book\"}");
    assertEquals(201, r.statusCode(), r.body());
    assertEquals(2, JSON.readTree(r.body()).get("bookId").asLong());
    error(post("/api/comments", "{\"author\":\"A\",\"text\":\"B\"}"), 400);
  }

  @Test
  void createsBookAndReadsLocation() throws Exception {
    var r =
        post(
            "/api/books",
            "{\"title\":\"Microframework Lab\",\"author\":\"Student\",\"pubYear\":2026}");
    assertEquals(201, r.statusCode(), r.body());
    assertTrue(
        get(r.headers().firstValue("location").orElseThrow())
            .body()
            .contains("Microframework Lab"));
  }

  @Test
  void contentTypeAndNullBodyAreRejected() throws Exception {
    error(post("/api/books/1/comments", "null"), 400);
    var r =
        client.send(
            HttpRequest.newBuilder(URI.create(base + "/api/books/1/comments"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("text"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    error(r, 415);
  }

  @Test
  void queryFiltersCommentsAndMissingQueryUsesDefaults() throws Exception {
    post("/api/books/1/comments", "{\"author\":\"FilterAuthor\",\"text\":\"Filtered\"}");
    var j =
        JSON.readTree(
            get("/api/books/1/comments?author=FilterAuthor&since=2026-10-29T00:00:00Z&size=1")
                .body());
    assertEquals(1, j.get("total").asInt());
    assertEquals("Filtered", j.get("items").get(0).get("text").asText());
    assertEquals(200, get("/api/books").statusCode());
  }

  @Test
  void unsupportedMethodIs405Json() throws Exception {
    var r =
        client.send(
            HttpRequest.newBuilder(URI.create(base + "/api/books"))
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString());
    error(r, 405);
    assertTrue(r.headers().firstValue("allow").orElseThrow().contains("GET"));
  }
}
