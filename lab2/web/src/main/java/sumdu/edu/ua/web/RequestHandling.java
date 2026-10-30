package sumdu.edu.ua.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpResponseException;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.edu.sumdu.core.error.ConflictException;
import ua.edu.sumdu.core.error.NotFoundException;
import ua.edu.sumdu.core.error.ValidationException;

public final class RequestHandling {
  private static final Logger log = LoggerFactory.getLogger(RequestHandling.class);

  private RequestHandling() {}

  public static void register(Javalin app, Clock clock) {
    app.before(
        ctx -> {
          String requestId = UUID.randomUUID().toString();
          ctx.attribute("requestId", requestId);
          ctx.header("X-Request-Id", requestId);
          log.info("{} {} requestId={}", ctx.method(), ctx.path(), requestId);
        });
    app.after(
        ctx ->
            log.info(
                "Response {} {} status={} requestId={}",
                ctx.method(),
                ctx.path(),
                ctx.statusCode(),
                ctx.attribute("requestId")));
    app.exception(ValidationException.class, (e, ctx) -> error(ctx, 400, e.getMessage(), clock));
    app.exception(NotFoundException.class, (e, ctx) -> error(ctx, 404, e.getMessage(), clock));
    app.exception(ConflictException.class, (e, ctx) -> error(ctx, 409, e.getMessage(), clock));
    app.exception(
        JsonProcessingException.class, (e, ctx) -> error(ctx, 400, "Некоректний JSON", clock));
    app.exception(
        HttpResponseException.class, (e, ctx) -> error(ctx, e.getStatus(), e.getMessage(), clock));
    app.exception(
        Exception.class,
        (e, ctx) -> {
          log.error("Request processing error requestId=" + ctx.attribute("requestId"), e);
          error(ctx, 500, "Внутрішня помилка сервера", clock);
        });
    app.error(404, ctx -> error(ctx, 404, "Маршрут не знайдено", clock));
    app.error(
        405,
        ctx -> {
          String path = ctx.path();
          ctx.header(
              "Allow",
              path.matches("/api/books/[^/]+/comments/[^/]+")
                  ? "DELETE"
                  : path.equals("/api/comments")
                      ? "POST"
                      : path.matches("/api/books/[^/]+") ? "GET" : "GET, POST");
          error(ctx, 405, "Метод не підтримується", clock);
        });
  }

  private static void error(Context ctx, int status, String message, Clock clock) {
    String code =
        switch (status) {
          case 400 -> "BAD_REQUEST";
          case 404 -> "NOT_FOUND";
          case 405 -> "METHOD_NOT_ALLOWED";
          case 409 -> "CONFLICT";
          case 415 -> "UNSUPPORTED_MEDIA_TYPE";
          default -> "INTERNAL_ERROR";
        };
    if (status < 500)
      log.warn(
          "{} {} status={} requestId={}",
          ctx.method(),
          ctx.path(),
          status,
          ctx.attribute("requestId"));
    ctx.status(status)
        .json(
            Map.of(
                "error",
                Map.of(
                    "status",
                    status,
                    "code",
                    code,
                    "message",
                    message,
                    "path",
                    ctx.path(),
                    "timestamp",
                    clock.instant().toString(),
                    "requestId",
                    (String) ctx.attribute("requestId"))));
  }
}
