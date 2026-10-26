package app.web;

import app.db.CommentDao;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

public final class AppInit implements ServletContextListener {
    private static final Logger log = LoggerFactory.getLogger(AppInit.class);

    @Override
    public void contextInitialized(ServletContextEvent event) {
        try {
            Files.createDirectories(Path.of("data"));
            new CommentDao().init();
            log.info("Guestbook database initialized");
        } catch (IOException | SQLException error) {
            log.error("Cannot initialize guestbook database", error);
            throw new IllegalStateException("Cannot initialize guestbook database", error);
        }
    }
}
