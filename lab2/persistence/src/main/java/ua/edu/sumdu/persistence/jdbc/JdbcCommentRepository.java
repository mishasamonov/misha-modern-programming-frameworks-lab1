package ua.edu.sumdu.persistence.jdbc;

import java.sql.*;
import java.time.*;
import java.util.*;
import ua.edu.sumdu.core.domain.*;
import ua.edu.sumdu.core.error.StorageException;
import ua.edu.sumdu.core.port.CommentRepositoryPort;

public final class JdbcCommentRepository implements CommentRepositoryPort {
  private final Db db;

  public JdbcCommentRepository(Db db) {
    this.db = db;
  }

  private Comment read(ResultSet r) throws SQLException {
    return new Comment(
        r.getLong("id"),
        r.getLong("book_id"),
        r.getString("author"),
        r.getString("text"),
        r.getObject("created_at", OffsetDateTime.class).toInstant());
  }

  public Comment add(long bookId, String author, String text, Instant at) {
    try (var c = db.open();
        var ps =
            c.prepareStatement(
                "INSERT INTO comments(book_id,author,text,created_at) VALUES(?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
      ps.setLong(1, bookId);
      ps.setString(2, author);
      ps.setString(3, text);
      ps.setObject(4, at.atOffset(ZoneOffset.UTC));
      ps.executeUpdate();
      try (var r = ps.getGeneratedKeys()) {
        if (!r.next()) throw new SQLException("Missing generated id");
        return new Comment(r.getLong(1), bookId, author, text, at);
      }
    } catch (SQLException e) {
      throw new StorageException("Cannot add comment", e);
    }
  }

  public Page<Comment> list(long bookId, String author, Instant since, PageRequest request) {
    String where = " WHERE book_id=?";
    boolean hasAuthor = author != null && !author.isBlank();
    if (hasAuthor) where += " AND LOWER(author) LIKE ? ESCAPE '!'";
    if (since != null) where += " AND created_at >= ?";
    String[] sort = request.sort().split(",");
    String column =
        switch (sort[0]) {
          case "author" -> "author";
          case "createdAt" -> "created_at";
          default -> "id";
        };
    String order = column + (sort[1].equals("asc") ? " ASC" : " DESC") + ", id DESC";
    try (var c = db.open()) {
      c.setAutoCommit(false);
      c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
      long total;
      try (var ps = c.prepareStatement("SELECT COUNT(*) FROM comments" + where)) {
        bind(ps, bookId, author, since);
        try (var r = ps.executeQuery()) {
          r.next();
          total = r.getLong(1);
        }
      }
      List<Comment> items = new ArrayList<>();
      try (var ps =
          c.prepareStatement(
              "SELECT * FROM comments" + where + " ORDER BY " + order + " LIMIT ? OFFSET ?")) {
        int i = bind(ps, bookId, author, since);
        ps.setInt(i++, request.size());
        ps.setLong(i, request.offset());
        try (var r = ps.executeQuery()) {
          while (r.next()) items.add(read(r));
        }
      }
      c.commit();
      return new Page<>(items, request, total);
    } catch (SQLException e) {
      throw new StorageException("Cannot list comments", e);
    }
  }

  private int bind(PreparedStatement ps, long bookId, String author, Instant since)
      throws SQLException {
    int i = 1;
    ps.setLong(i++, bookId);
    if (author != null && !author.isBlank())
      ps.setString(
          i++,
          "%"
              + author
                  .toLowerCase(Locale.ROOT)
                  .replace("!", "!!")
                  .replace("%", "!%")
                  .replace("_", "!_")
              + "%");
    if (since != null) ps.setObject(i++, since.atOffset(ZoneOffset.UTC));
    return i;
  }

  public Optional<Comment> findById(long bookId, long commentId) {
    try (var c = db.open();
        var ps = c.prepareStatement("SELECT * FROM comments WHERE book_id=? AND id=?")) {
      ps.setLong(1, bookId);
      ps.setLong(2, commentId);
      try (var r = ps.executeQuery()) {
        return r.next() ? Optional.of(read(r)) : Optional.empty();
      }
    } catch (SQLException e) {
      throw new StorageException("Cannot find comment", e);
    }
  }

  public boolean delete(long bookId, long commentId) {
    try (var c = db.open();
        var ps = c.prepareStatement("DELETE FROM comments WHERE book_id=? AND id=?")) {
      ps.setLong(1, bookId);
      ps.setLong(2, commentId);
      return ps.executeUpdate() == 1;
    } catch (SQLException e) {
      throw new StorageException("Cannot delete comment", e);
    }
  }
}
