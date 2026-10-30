package sumdu.edu.ua.web;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.UnsupportedMediaTypeResponse;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import ua.edu.sumdu.core.domain.PageRequest;
import ua.edu.sumdu.core.error.ValidationException;
import ua.edu.sumdu.core.service.CatalogService;
import ua.edu.sumdu.core.service.CommentService;

public final class BooksController {
  private final CatalogService catalog;
  private final CommentService comments;

  public BooksController(CatalogService catalog, CommentService comments) {
    this.catalog = catalog;
    this.comments = comments;
  }

  public void registerRoutes(Javalin app) {
    app.get("/api/books", this::getBooks);
    app.get("/api/books/{id}", this::getBook);
    app.post("/api/books", this::addBook);
    app.get("/api/books/{id}/comments", this::getComments);
    app.post("/api/books/{id}/comments", this::addComment);
    app.delete("/api/books/{id}/comments/{commentId}", this::deleteComment);
    app.post("/api/comments", this::addCommentFromBody);
  }

  private void getBooks(Context ctx) {
    String q = ctx.queryParam("q");
    ctx.json(catalog.search(q, paging(ctx, false)));
  }

  private void getBook(Context ctx) {
    long id = positiveId(ctx.pathParam("id"));
    ctx.json(catalog.get(id));
  }

  private void addBook(Context ctx) {
    BookRequest request = body(ctx, BookRequest.class);
    if (request.pubYear() == null) throw new ValidationException("pubYear є обов'язковим");
    var book = catalog.add(request.title(), request.author(), request.pubYear());
    ctx.header("Location", "/api/books/" + book.id()).status(201).json(book);
  }

  private void getComments(Context ctx) {
    Instant since = null;
    try {
      if (ctx.queryParam("since") != null) since = Instant.parse(ctx.queryParam("since"));
    } catch (DateTimeParseException e) {
      throw new ValidationException("since має бути датою ISO 8601 UTC");
    }
    ctx.json(
        comments.list(
            positiveId(ctx.pathParam("id")), ctx.queryParam("author"), since, paging(ctx, true)));
  }

  private void addComment(Context ctx) {
    CommentRequest request = body(ctx, CommentRequest.class);
    createComment(ctx, positiveId(ctx.pathParam("id")), request.author(), request.text());
  }

  private void addCommentFromBody(Context ctx) {
    CommentRequest request = body(ctx, CommentRequest.class);
    if (request.bookId() == null || request.bookId() <= 0)
      throw new ValidationException("bookId має бути додатним");
    createComment(ctx, request.bookId(), request.author(), request.text());
  }

  private void createComment(Context ctx, long bookId, String author, String text) {
    var comment = comments.add(bookId, author, text);
    ctx.header("Location", "/api/books/" + bookId + "/comments/" + comment.id())
        .status(201)
        .json(comment);
  }

  private void deleteComment(Context ctx) {
    comments.delete(positiveId(ctx.pathParam("id")), positiveId(ctx.pathParam("commentId")));
    ctx.status(204);
  }

  private PageRequest paging(Context ctx, boolean forComments) {
    String sort = ctx.queryParam("sort");
    return new PageRequest(
        number(ctx, "page", 0),
        number(ctx, "size", 10),
        sort == null ? (forComments ? "createdAt,desc" : "id,asc") : sort);
  }

  private int number(Context ctx, String name, int fallback) {
    String value = ctx.queryParam(name);
    if (value == null) return fallback;
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new ValidationException(name + ": очікується ціле число");
    }
  }

  private long positiveId(String value) {
    try {
      long id = Long.parseLong(value);
      if (id > 0) return id;
    } catch (NumberFormatException ignored) {
    }
    throw new ValidationException("id: очікується додатне ціле число");
  }

  private <T> T body(Context ctx, Class<T> type) {
    if (ctx.contentType() == null
        || !ctx.contentType()
            .toLowerCase(Locale.ROOT)
            .split(";", 2)[0]
            .trim()
            .equals("application/json"))
      throw new UnsupportedMediaTypeResponse("Використайте Content-Type: application/json");
    if (ctx.body().trim().equals("null")) throw new ValidationException("Очікується JSON-об'єкт");
    T request = ctx.bodyAsClass(type);
    if (request == null) throw new ValidationException("Очікується JSON-об'єкт");
    return request;
  }

  public record BookRequest(String title, String author, Integer pubYear) {}

  public record CommentRequest(Long bookId, String author, String text) {}
}
