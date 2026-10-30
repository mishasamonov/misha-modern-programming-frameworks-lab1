package ua.edu.sumdu.core.domain;

import java.util.Set;
import ua.edu.sumdu.core.error.ValidationException;

public record PageRequest(int page, int size, String sort) {
  public PageRequest {
    if (page < 0) throw new ValidationException("page має бути не менше 0");
    if (size < 1 || size > 100) throw new ValidationException("size має бути від 1 до 100");
    if (sort == null) sort = "id,desc";
    if (!Set.of(
            "id,asc",
            "id,desc",
            "title,asc",
            "title,desc",
            "author,asc",
            "author,desc",
            "pubYear,asc",
            "pubYear,desc",
            "createdAt,asc",
            "createdAt,desc")
        .contains(sort)) throw new ValidationException("Некоректне значення sort");
  }

  public long offset() {
    return (long) page * size;
  }
}
