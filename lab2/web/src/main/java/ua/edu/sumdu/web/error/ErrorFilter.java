package ua.edu.sumdu.web.error;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import org.slf4j.*;
import ua.edu.sumdu.core.error.*;
import ua.edu.sumdu.web.http.Json;

public final class ErrorFilter implements Filter {
  private static final Logger log = LoggerFactory.getLogger(ErrorFilter.class);

  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    var req = (HttpServletRequest) request;
    var resp = (HttpServletResponse) response;
    req.setCharacterEncoding("UTF-8");
    resp.setCharacterEncoding("UTF-8");
    String requestId = UUID.randomUUID().toString();
    resp.setHeader("X-Request-Id", requestId);
    resp.setHeader("X-Content-Type-Options", "nosniff");
    try {
      chain.doFilter(req, resp);
    } catch (Exception e) {
      int status =
          e instanceof ValidationException
              ? 400
              : e instanceof NotFoundException
                  ? 404
                  : e instanceof ConflictException
                      ? 409
                      : e instanceof HttpException h ? h.status() : 500;
      if (status >= 500)
        log.error(
            "requestId={} method={} path={} status={}",
            requestId,
            req.getMethod(),
            req.getRequestURI(),
            status,
            e);
      else
        log.warn(
            "requestId={} method={} path={} status={} message={}",
            requestId,
            req.getMethod(),
            req.getRequestURI(),
            status,
            e.getMessage().replaceAll("[\r\n]", " "));
      if (resp.isCommitted()) throw new ServletException(e);
      resp.resetBuffer();
      Json.write(
          resp,
          status,
          Map.of(
              "error",
              Map.of(
                  "status",
                  status,
                  "code",
                  switch (status) {
                    case 400 -> "BAD_REQUEST";
                    case 404 -> "NOT_FOUND";
                    case 409 -> "CONFLICT";
                    case 405 -> "METHOD_NOT_ALLOWED";
                    default -> "INTERNAL_ERROR";
                  },
                  "message",
                  status >= 500 ? "Внутрішня помилка сервера" : e.getMessage(),
                  "path",
                  req.getRequestURI(),
                  "timestamp",
                  Instant.now(),
                  "requestId",
                  requestId)));
    }
  }
}
