package ua.edu.sumdu.web.config;

import jakarta.servlet.*;
import java.time.Clock;
import java.util.ServiceLoader;
import org.slf4j.LoggerFactory;
import ua.edu.sumdu.core.port.RepositoryProvider;
import ua.edu.sumdu.core.service.*;

public final class AppInit implements ServletContextListener {
  public void contextInitialized(ServletContextEvent event) {
    if (event.getServletContext().getAttribute("services") != null) return;
    var providers = ServiceLoader.load(RepositoryProvider.class).iterator();
    if (!providers.hasNext()) throw new IllegalStateException("Repository provider is missing");
    var provider = providers.next();
    if (providers.hasNext())
      throw new IllegalStateException("Exactly one repository provider is required");
    var catalog = new CatalogService(provider.catalog());
    event
        .getServletContext()
        .setAttribute(
            "services",
            new Services(
                catalog, new CommentService(provider.comments(), catalog, Clock.systemUTC())));
    LoggerFactory.getLogger(AppInit.class).info("Catalog started: http://localhost:8081/books");
  }
}
