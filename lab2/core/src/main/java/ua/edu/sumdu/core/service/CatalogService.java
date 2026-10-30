package ua.edu.sumdu.core.service;

import ua.edu.sumdu.core.domain.*;
import ua.edu.sumdu.core.error.*;
import ua.edu.sumdu.core.port.CatalogRepositoryPort;

public final class CatalogService {
  private final CatalogRepositoryPort repo;

  public CatalogService(CatalogRepositoryPort repo) {
    this.repo = repo;
  }

  public Book get(long id) {
    if (id <= 0) throw new ValidationException("id має бути додатним");
    return repo.findById(id).orElseThrow(() -> new NotFoundException("Книгу не знайдено"));
  }

  public Page<Book> search(String query, PageRequest request) {
    String q = query == null ? "" : query.trim();
    if (q.length() > 255) throw new ValidationException("q має містити не більше 255 символів");
    if (request.sort().startsWith("createdAt"))
      throw new ValidationException("Для книг не підтримується сортування createdAt");
    return repo.search(q, request);
  }

  public Book add(String title, String author, int year) {
    title = clean(title, 255, "Назва");
    author = clean(author, 255, "Автор");
    if (year < 1 || year > 9999) throw new ValidationException("Некоректний рік видання");
    return repo.add(title, author, year);
  }

  public static String clean(String value, int max, String field) {
    if (value == null || value.isBlank() || value.trim().length() > max)
      throw new ValidationException(field + ": обов'язкове значення довжиною до " + max);
    return value.trim();
  }
}
