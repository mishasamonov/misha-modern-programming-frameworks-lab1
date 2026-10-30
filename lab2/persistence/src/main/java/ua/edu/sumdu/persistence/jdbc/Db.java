package ua.edu.sumdu.persistence.jdbc;

import java.nio.file.*;
import java.sql.*;
import ua.edu.sumdu.core.error.StorageException;

public final class Db {
  private final String url;

  public Db(String url) {
    this.url = url;
  }

  public Connection open() throws SQLException {
    return DriverManager.getConnection(url, "sa", "");
  }

  public static Db configured() {
    try {
      Files.createDirectories(Path.of("data"));
      Class.forName("org.h2.Driver");
    } catch (Exception e) {
      throw new StorageException("Cannot initialize database", e);
    }
    return new Db(
        System.getProperty("catalog.db.url", "jdbc:h2:file:./data/catalog;AUTO_SERVER=TRUE"));
  }

  public void initialize() {
    try (var in = Db.class.getResourceAsStream("/schema.sql");
        var c = open();
        var st = c.createStatement()) {
      if (in == null) throw new IllegalStateException("schema.sql is missing");
      for (String sql :
          new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split(";"))
        if (!sql.isBlank()) st.execute(sql);
    } catch (Exception e) {
      throw new StorageException("Cannot initialize schema", e);
    }
  }
}
