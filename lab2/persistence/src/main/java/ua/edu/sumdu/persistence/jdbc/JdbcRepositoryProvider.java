package ua.edu.sumdu.persistence.jdbc;

import ua.edu.sumdu.core.port.*;

public final class JdbcRepositoryProvider implements RepositoryProvider {
  private final CatalogRepositoryPort catalog;
  private final CommentRepositoryPort comments;

  public JdbcRepositoryProvider() {
    Db db = Db.configured();
    db.initialize();
    catalog = new JdbcBookRepository(db);
    comments = new JdbcCommentRepository(db);
  }

  public CatalogRepositoryPort catalog() {
    return catalog;
  }

  public CommentRepositoryPort comments() {
    return comments;
  }
}
