package sumdu.edu.ua.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import java.time.Clock;
import java.util.ServiceLoader;
import ua.edu.sumdu.core.port.RepositoryProvider;
import ua.edu.sumdu.core.service.CatalogService;
import ua.edu.sumdu.core.service.CommentService;

/** Точка входу лабораторної 3. Використовує лише порти та сервіси core. */
public final class JavalinBookApp {
  private JavalinBookApp() {}

  public static Javalin create(CatalogService catalog, CommentService comments, Clock clock) {
    ObjectMapper mapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    Javalin app =
        Javalin.create(
            config -> {
              config.jsonMapper(new JavalinJackson(mapper, false));
              config.http.prefer405over404 = true;
              config.showJavalinBanner = false;
            });
    RequestHandling.register(app, clock);
    app.get("/", ctx -> ctx.result("Book catalogue — Javalin 6.2.0"));
    new BooksController(catalog, comments).registerRoutes(app);
    return app;
  }

  public static void main(String[] args) {
    RepositoryProvider provider =
        ServiceLoader.load(RepositoryProvider.class)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("RepositoryProvider не знайдено"));
    Clock clock = Clock.systemUTC();
    CatalogService catalog = new CatalogService(provider.catalog());
    CommentService comments = new CommentService(provider.comments(), catalog, clock);
    Javalin app = create(catalog, comments, clock);
    app.start("127.0.0.1", Integer.getInteger("catalog.port", 8080));
    Runtime.getRuntime().addShutdownHook(new Thread(app::stop));
  }
}
