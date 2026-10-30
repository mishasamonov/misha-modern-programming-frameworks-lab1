package ua.edu.sumdu.core.service;

import java.time.*;
import ua.edu.sumdu.core.domain.*;
import ua.edu.sumdu.core.error.*;
import ua.edu.sumdu.core.port.CommentRepositoryPort;

public final class CommentService {
  private final CommentRepositoryPort repo;
  private final CatalogService catalog;
  private final Clock clock;

  public CommentService(CommentRepositoryPort repo, CatalogService catalog, Clock clock) {
    this.repo = repo;
    this.catalog = catalog;
    this.clock = clock;
  }

  public Comment add(long bookId, String author, String text) {
    catalog.get(bookId);
    return repo.add(
        bookId,
        CatalogService.clean(author, 64, "Автор"),
        CatalogService.clean(text, 1000, "Текст"),
        clock.instant());
  }

  public Page<Comment> list(long bookId, String author, Instant since, PageRequest request) {
    catalog.get(bookId);
    if (author != null && author.length() > 64)
      throw new ValidationException("Автор: не більше 64 символів");
    if (!(request.sort().startsWith("id,")
        || request.sort().startsWith("createdAt,")
        || request.sort().startsWith("author,")))
      throw new ValidationException("Некоректне сортування відгуків");
    return repo.list(bookId, author == null ? null : author.trim(), since, request);
  }

  public Comment delete(long bookId, long commentId) {
    catalog.get(bookId);
    if (commentId <= 0) throw new ValidationException("commentId має бути додатним");
    Comment comment =
        repo.findById(bookId, commentId)
            .orElseThrow(() -> new NotFoundException("Відгук не знайдено"));
    Instant now = clock.instant();
    if (now.isBefore(comment.createdAt())
        || !now.isBefore(comment.createdAt().plus(Duration.ofHours(24))))
      throw new ConflictException("Відгук можна видалити лише протягом 24 годин після створення");
    if (!repo.delete(bookId, commentId)) throw new NotFoundException("Відгук уже видалено");
    return comment;
  }
}
