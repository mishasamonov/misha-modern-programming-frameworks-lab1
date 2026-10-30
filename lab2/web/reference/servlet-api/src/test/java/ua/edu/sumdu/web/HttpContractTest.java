package ua.edu.sumdu.web;

import static org.junit.jupiter.api.Assertions.*;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.webapp.WebAppContext;
import org.junit.jupiter.api.*;
import ua.edu.sumdu.core.domain.*;
import ua.edu.sumdu.core.error.StorageException;
import ua.edu.sumdu.core.port.*;
import ua.edu.sumdu.core.service.*;
import ua.edu.sumdu.web.config.Services;
import ua.edu.sumdu.web.http.Json;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HttpContractTest {
  Server server;
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
    server = new Server(new java.net.InetSocketAddress("127.0.0.1", 0));
    var context = new WebAppContext();
    context.setContextPath("/");
    context.setResourceBase("src/main/webapp");
    context.setDescriptor("src/main/webapp/WEB-INF/web.xml");
    context.setParentLoaderPriority(true);
    context.setAttribute(
        "services", new Services(catalog, new CommentService(provider.comments(), catalog, clock)));
    server.setHandler(context);
    server.start();
    base = "http://127.0.0.1:" + ((ServerConnector) server.getConnectors()[0]).getLocalPort();
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
        HttpRequest.newBuilder(URI.create(base + "/books/1/comments/" + id)).DELETE().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  void error(HttpResponse<String> r, int status) throws Exception {
    assertEquals(status, r.statusCode(), r.body());
    assertTrue(
        r.headers()
            .firstValue("content-type")
            .orElse("")
            .contains("application/json;charset=utf-8"));
    var e = Json.MAPPER.readTree(r.body()).get("error");
    assertEquals(status, e.get("status").asInt());
    assertNotNull(e.get("requestId"));
    assertEquals(r.headers().firstValue("X-Request-Id").orElseThrow(), e.get("requestId").asText());
    assertNotNull(e.get("timestamp"));
  }

  @Test
  void booksSearchPaginationAndUtf8() throws Exception {
    var r = get("/books?q=Java&page=0&size=1&sort=title,asc");
    assertEquals(200, r.statusCode());
    var j = Json.MAPPER.readTree(r.body());
    assertEquals(2, j.get("total").asInt());
    assertEquals(1, j.get("items").size());
    assertEquals(1, j.get("request").get("size").asInt());
    assertTrue(get("/books/1").body().contains("Чистий код"));
  }

  @Test
  void htmlPagesAndEscaping() throws Exception {
    var r =
        client.send(
            HttpRequest.newBuilder(URI.create(base + "/books/1"))
                .header("Accept", "text/html")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(200, r.statusCode());
    assertTrue(r.body().contains("Додати відгук"));
    assertTrue(r.headers().firstValue("content-type").orElse("").contains("text/html"));
    assertTrue(
        ua.edu.sumdu.web.http.Html.catalog(
                new Page<>(
                    List.of(new Book(1, "<script>", "A", 2000)),
                    new PageRequest(0, 10, "id,asc"),
                    1),
                "\" onfocus=evil",
                "")
            .contains("&lt;script&gt;"));
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
            "?sort=createdAt,asc")) error(get("/books" + p), 400);
    error(get("/books/no-number"), 400);
    error(get("/books/1/comments?since=no-date"), 400);
  }

  @Test
  void missingResourcesAre404() throws Exception {
    error(get("/books/999999"), 404);
    error(get("/unknown"), 404);
    error(get("/books/999999/comments"), 404);
    error(delete(999999), 404);
  }

  @Test
  void malformedAndInvalidPostAre400() throws Exception {
    error(post("/books/1/comments", "{"), 400);
    error(post("/books/1/comments", "{\"author\":\" \",\"text\":\"Text\"}"), 400);
    error(
        post("/books/1/comments", "{\"author\":\"A\",\"text\":\"" + "a".repeat(1001) + "\"}"), 400);
  }

  @Test
  void createsAndDeletesComment() throws Exception {
    var r = post("/books/1/comments", "{\"author\":\"Михайло\",\"text\":\"Корисна книга\"}");
    assertEquals(201, r.statusCode(), r.body());
    long id = Json.MAPPER.readTree(r.body()).get("id").asLong();
    assertTrue(r.headers().firstValue("location").orElseThrow().endsWith("/" + id));
    assertEquals(204, delete(id).statusCode());
    error(delete(id), 404);
  }

  @Test
  void deletionAt24HoursIs409AndKeepsComment() throws Exception {
    var r = post("/books/1/comments", "{\"author\":\"Михайло\",\"text\":\"Старий відгук\"}");
    long id = Json.MAPPER.readTree(r.body()).get("id").asLong();
    time = start.plus(Duration.ofHours(24));
    error(delete(id), 409);
    assertTrue(get("/books/1/comments").body().contains("Старий відгук"));
  }

  @Test
  void storageFailureIs500WithoutInternalDetails() throws Exception {
    var r = get("/books?q=storage-failure-test");
    error(r, 500);
    assertFalse(r.body().contains("Simulated"));
    assertFalse(r.body().contains("StorageException"));
  }

  @Test
  void unsupportedMethodIs405Json() throws Exception {
    var r =
        client.send(
            HttpRequest.newBuilder(URI.create(base + "/books"))
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString());
    error(r, 405);
    assertTrue(r.headers().firstValue("allow").orElseThrow().contains("GET"));
  }
}
