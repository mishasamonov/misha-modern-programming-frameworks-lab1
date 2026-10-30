package ua.edu.sumdu.persistence.jdbc;

import java.sql.*;
import java.util.*;
import ua.edu.sumdu.core.domain.*;
import ua.edu.sumdu.core.error.StorageException;
import ua.edu.sumdu.core.port.CatalogRepositoryPort;

public final class JdbcBookRepository implements CatalogRepositoryPort {
  private final Db db;

  public JdbcBookRepository(Db db) {
    this.db = db;
  }

  private Book read(ResultSet r) throws SQLException {
    return new Book(
        r.getLong("id"), r.getString("title"), r.getString("author"), r.getInt("pub_year"));
  }

  public Page<Book> search(String query, PageRequest request) {
    String pattern =
        "%"
            + query
                .toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_")
            + "%";
    String where = " WHERE LOWER(title) LIKE ? ESCAPE '!' OR LOWER(author) LIKE ? ESCAPE '!'";
    String[] sort = request.sort().split(",");
    String column =
        switch (sort[0]) {
          case "title" -> "title";
          case "author" -> "author";
          case "pubYear" -> "pub_year";
          default -> "id";
        };
    String order = column + (sort[1].equals("asc") ? " ASC" : " DESC") + ", id ASC";
    try (var c = db.open()) {
      c.setAutoCommit(false);
      c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
      long total;
      try (var ps = c.prepareStatement("SELECT COUNT(*) FROM books" + where)) {
        ps.setString(1, pattern);
        ps.setString(2, pattern);
        try (var r = ps.executeQuery()) {
          r.next();
          total = r.getLong(1);
        }
      }
      List<Book> items = new ArrayList<>();
      try (var ps =
          c.prepareStatement(
              "SELECT * FROM books" + where + " ORDER BY " + order + " LIMIT ? OFFSET ?")) {
        ps.setString(1, pattern);
        ps.setString(2, pattern);
        ps.setInt(3, request.size());
        ps.setLong(4, request.offset());
        try (var r = ps.executeQuery()) {
          while (r.next()) items.add(read(r));
        }
      }
      c.commit();
      return new Page<>(items, request, total);
    } catch (SQLException e) {
      throw new StorageException("Cannot search books", e);
    }
  }

  public Optional<Book> findById(long id) {
    try (var c = db.open();
        var ps = c.prepareStatement("SELECT * FROM books WHERE id=?")) {
      ps.setLong(1, id);
      try (var r = ps.executeQuery()) {
        return r.next() ? Optional.of(read(r)) : Optional.empty();
      }
    } catch (SQLException e) {
      throw new StorageException("Cannot load book", e);
    }
  }

  public Book add(String title, String author, int year) {
    try (var c = db.open();
        var ps =
            c.prepareStatement(
                "INSERT INTO books(title,author,pub_year) VALUES(?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
      ps.setString(1, title);
      ps.setString(2, author);
      ps.setInt(3, year);
      ps.executeUpdate();
      try (var r = ps.getGeneratedKeys()) {
        if (!r.next()) throw new SQLException("Missing generated id");
        return new Book(r.getLong(1), title, author, year);
      }
    } catch (SQLException e) {
      throw new StorageException("Cannot add book", e);
    }
  }
}
