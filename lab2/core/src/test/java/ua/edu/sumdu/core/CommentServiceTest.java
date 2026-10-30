package ua.edu.sumdu.core;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.edu.sumdu.core.domain.*;
import ua.edu.sumdu.core.error.*;
import ua.edu.sumdu.core.port.*;
import ua.edu.sumdu.core.service.*;

class CommentServiceTest {
  final Instant now = Instant.parse("2026-10-29T12:00:00Z");
  final Map<Long, Comment> data = new HashMap<>();
  final CatalogRepositoryPort books =
      new CatalogRepositoryPort() {
        public Optional<Book> findById(long id) {
          return id == 1 ? Optional.of(new Book(1, "Java", "Блох", 2018)) : Optional.empty();
        }

        public Book add(String t, String a, int y) {
          throw new UnsupportedOperationException();
        }

        public Page<Book> search(String q, PageRequest p) {
          return new Page<>(List.of(), p, 0);
        }
      };
  final CommentRepositoryPort comments =
      new CommentRepositoryPort() {
        public Optional<Comment> findById(long b, long id) {
          return Optional.ofNullable(data.get(id)).filter(c -> c.bookId() == b);
        }

        public boolean delete(long b, long id) {
          return data.remove(id) != null;
        }

        public Comment add(long b, String a, String t, Instant at) {
          var c = new Comment(1, b, a, t, at);
          data.put(1L, c);
          return c;
        }

        public Page<Comment> list(long b, String a, Instant s, PageRequest p) {
          return new Page<>(List.copyOf(data.values()), p, data.size());
        }
      };
  final CatalogService catalog = new CatalogService(books);
  final CommentService service =
      new CommentService(comments, catalog, Clock.fixed(now, ZoneOffset.UTC));

  @Test
  void deletesJustBefore24Hours() {
    data.put(1L, new Comment(1, 1, "A", "Text", now.minus(Duration.ofHours(24)).plusNanos(1)));
    service.delete(1, 1);
    assertTrue(data.isEmpty());
  }

  @Test
  void refusesAtExactly24Hours() {
    data.put(1L, new Comment(1, 1, "A", "Text", now.minus(Duration.ofHours(24))));
    assertThrows(ConflictException.class, () -> service.delete(1, 1));
    assertEquals(1, data.size());
  }

  @Test
  void refusesAfter24Hours() {
    data.put(1L, new Comment(1, 1, "A", "Text", now.minus(Duration.ofHours(24)).minusNanos(1)));
    assertThrows(ConflictException.class, () -> service.delete(1, 1));
    assertEquals(1, data.size());
  }

  @Test
  void refusesFutureTimestamp() {
    data.put(1L, new Comment(1, 1, "A", "Text", now.plusSeconds(1)));
    assertThrows(ConflictException.class, () -> service.delete(1, 1));
  }

  @Test
  void usesStoredTimeAndTrimsFields() {
    var c = service.add(1, "  Михайло  ", "  Корисна книга  ");
    assertEquals(now, c.createdAt());
    assertEquals("Михайло", c.author());
    assertEquals("Корисна книга", c.text());
  }

  @Test
  void rejectsBlankFields() {
    assertThrows(ValidationException.class, () -> service.add(1, " ", "Text"));
    assertThrows(ValidationException.class, () -> service.add(1, "A", " "));
  }

  @Test
  void rejectsOversizedFields() {
    assertThrows(ValidationException.class, () -> service.add(1, "a".repeat(65), "Text"));
    assertThrows(ValidationException.class, () -> service.add(1, "A", "t".repeat(1001)));
  }

  @Test
  void missingBookAndCommentAre404() {
    assertThrows(NotFoundException.class, () -> service.add(2, "A", "Text"));
    assertThrows(NotFoundException.class, () -> service.delete(1, 999));
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0, 101})
  void rejectsInvalidSizes(int size) {
    assertThrows(ValidationException.class, () -> new PageRequest(0, size, "id,asc"));
  }

  @Test
  void rejectsInvalidPagingAndSort() {
    assertThrows(ValidationException.class, () -> new PageRequest(-1, 10, "id,asc"));
    assertThrows(ValidationException.class, () -> new PageRequest(0, 10, "id;DROP TABLE books"));
    assertThrows(
        ValidationException.class,
        () -> catalog.search("", new PageRequest(0, 10, "createdAt,asc")));
  }

  @Test
  void offsetDoesNotOverflow() {
    assertEquals(214748364700L, new PageRequest(Integer.MAX_VALUE, 100, "id,asc").offset());
  }
}
