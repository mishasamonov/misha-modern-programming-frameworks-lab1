package ua.edu.sumdu.web.http;

import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import org.slf4j.*;
import ua.edu.sumdu.core.domain.*;
import ua.edu.sumdu.core.error.*;
import ua.edu.sumdu.web.config.Services;
import ua.edu.sumdu.web.error.HttpException;

public final class BooksServlet extends HttpServlet {
  private static final Logger log = LoggerFactory.getLogger(BooksServlet.class);

  private Services services() {
    return (Services) getServletContext().getAttribute("services");
  }

  private int number(HttpServletRequest req, String name, int fallback) {
    String value = req.getParameter(name);
    if (value == null) return fallback;
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new ValidationException(name + ": очікується ціле число");
    }
  }

  private long id(String value) {
    try {
      long id = Long.parseLong(value);
      if (id <= 0) throw new NumberFormatException();
      return id;
    } catch (NumberFormatException e) {
      throw new ValidationException("id: очікується додатне ціле число");
    }
  }

  private PageRequest paging(HttpServletRequest req, boolean comments) {
    return new PageRequest(
        number(req, "page", 0),
        number(req, "size", 10),
        req.getParameter("sort") == null
            ? (comments ? "createdAt,desc" : "id,asc")
            : req.getParameter("sort"));
  }

  private Map<String, String> fields(HttpServletRequest req) throws IOException {
    if (req.getContentType() != null
        && req.getContentType().toLowerCase(Locale.ROOT).startsWith("application/json")) {
      try {
        var node = Json.MAPPER.readTree(req.getInputStream());
        if (node == null || !node.isObject())
          throw new ValidationException("Очікується JSON-об'єкт");
        Map<String, String> values = new HashMap<>();
        node.fields()
            .forEachRemaining(
                e -> {
                  if (!e.getValue().isNull()) values.put(e.getKey(), e.getValue().asText());
                });
        return values;
      } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
        throw new ValidationException("Некоректний JSON");
      }
    }
    if (req.getContentType() == null
        || !req.getContentType()
            .toLowerCase(Locale.ROOT)
            .startsWith("application/x-www-form-urlencoded"))
      throw new ValidationException("Використайте JSON або application/x-www-form-urlencoded");
    Map<String, String> values = new HashMap<>();
    req.getParameterMap().forEach((k, v) -> values.put(k, v[0]));
    return values;
  }

  @Override
  protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String path = req.getRequestURI().substring(req.getContextPath().length());
    boolean api = path.startsWith("/api/");
    if (api) path = path.substring(4);
    if (path.equals("/")) {
      resp.sendRedirect(req.getContextPath() + "/books");
      return;
    }
    String[] p = path.split("/", -1);
    if (p.length < 2
        || !p[1].equals("books")
        || p.length > 5
        || (p.length >= 4 && !p[3].equals("comments")))
      throw new NotFoundException("Маршрут не знайдено");
    boolean json =
        api
            || Optional.ofNullable(req.getHeader("Accept")).orElse("").contains("application/json")
            || p.length >= 4;
    resp.setHeader("Vary", "Accept");
    String method = req.getMethod();
    var s = services();
    if (p.length == 2 && method.equals("GET")) {
      var page = s.catalog().search(req.getParameter("q"), paging(req, false));
      if (json) Json.write(resp, 200, page);
      else html(resp, Html.catalog(page, req.getParameter("q"), req.getContextPath()));
      return;
    }
    if (p.length == 2 && method.equals("POST")) {
      var f = fields(req);
      int year;
      try {
        year = Integer.parseInt(f.getOrDefault("pubYear", ""));
      } catch (NumberFormatException e) {
        throw new ValidationException("Некоректний рік видання");
      }
      Book book = s.catalog().add(f.get("title"), f.get("author"), year);
      resp.setHeader("Location", req.getContextPath() + "/books/" + book.id());
      Json.write(resp, 201, book);
      return;
    }
    if (p.length >= 3) {
      long bookId = id(p[2]);
      if (p.length == 3 && method.equals("GET")) {
        Book book = s.catalog().get(bookId);
        if (json) Json.write(resp, 200, book);
        else
          html(
              resp,
              Html.book(
                  book,
                  s.comments().list(bookId, null, null, new PageRequest(0, 10, "createdAt,desc")),
                  req.getContextPath()));
        return;
      }
      if (p.length == 4 && method.equals("GET")) {
        Instant since = null;
        try {
          if (req.getParameter("since") != null) since = Instant.parse(req.getParameter("since"));
        } catch (java.time.format.DateTimeParseException e) {
          throw new ValidationException("since має бути датою ISO 8601 UTC");
        }
        Json.write(
            resp,
            200,
            s.comments().list(bookId, req.getParameter("author"), since, paging(req, true)));
        return;
      }
      if (p.length == 4 && method.equals("POST")) {
        var f = fields(req);
        Comment c = s.comments().add(bookId, f.get("author"), f.get("text"));
        log.info(
            "Comment created id={} bookId={} author={} length={}",
            c.id(),
            c.bookId(),
            c.author().replaceAll("[\r\n]", " "),
            c.text().length());
        resp.setHeader(
            "Location", req.getContextPath() + "/books/" + bookId + "/comments/" + c.id());
        Json.write(resp, 201, c);
        return;
      }
      if (p.length == 5 && method.equals("DELETE")) {
        Comment c = s.comments().delete(bookId, id(p[4]));
        log.info(
            "Comment deleted id={} bookId={} author={} createdAt={}",
            c.id(),
            c.bookId(),
            c.author().replaceAll("[\r\n]", " "),
            c.createdAt());
        resp.setStatus(204);
        return;
      }
    }
    resp.setHeader("Allow", p.length == 5 ? "DELETE" : p.length == 3 ? "GET" : "GET, POST");
    throw new HttpException(405, "Метод не підтримується");
  }

  private void html(HttpServletResponse resp, String value) throws IOException {
    resp.setContentType("text/html;charset=UTF-8");
    resp.getWriter().write(value);
  }
}
