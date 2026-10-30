package ua.edu.sumdu.core.port;

import java.time.Instant;
import java.util.Optional;
import ua.edu.sumdu.core.domain.*;

public interface CommentRepositoryPort {
  Comment add(long bookId, String author, String text, Instant createdAt);

  Page<Comment> list(long bookId, String author, Instant since, PageRequest request);

  Optional<Comment> findById(long bookId, long commentId);

  boolean delete(long bookId, long commentId);
}
