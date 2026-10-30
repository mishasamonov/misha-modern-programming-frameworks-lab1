package ua.edu.sumdu.core.port;

/** Runtime adapter discovered through Java ServiceLoader. */
public interface RepositoryProvider {
  CatalogRepositoryPort catalog();

  CommentRepositoryPort comments();
}
