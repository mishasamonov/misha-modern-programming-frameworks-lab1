package ua.edu.sumdu.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import ua.edu.sumdu.core.domain.*;
import ua.edu.sumdu.persistence.jdbc.*;

class JdbcRepositoriesTest {
  Db db;
  JdbcBookRepository books;
  JdbcCommentRepository comments;

  @BeforeEach
  void init() {
    db = new Db("jdbc:h2:mem:" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    db.initialize();
    books = new JdbcBookRepository(db);
    comments = new JdbcCommentRepository(db);
  }

  @Test
  void filteredTotalAndPaginationMatch() {
    var p = books.search("мартін", new PageRequest(0, 1, "title,asc"));
    assertEquals(3, p.total());
    assertEquals(1, p.items().size());
    var second = books.search("мартін", new PageRequest(1, 1, "title,asc"));
    assertNotEquals(p.items().get(0).id(), second.items().get(0).id());
  }

  @Test
  void sortsByYearAndEscapesLikeWildcards() {
    var p = books.search("", new PageRequest(0, 10, "pubYear,desc"));
    assertEquals(2018, p.items().get(0).pubYear());
    assertEquals(0, books.search("%", new PageRequest(0, 10, "id,asc")).total());
    assertEquals(0, books.search("' OR 1=1 --", new PageRequest(0, 10, "id,asc")).total());
  }

  @Test
  void filtersCommentTotalAndPreservesInstant() {
    Instant now = Instant.parse("2026-10-29T12:00:00.123456789Z");
    comments.add(1, "Михайло", "Текст", now);
    comments.add(1, "Олена", "Інший", now.minusSeconds(3600));
    var p = comments.list(1, "мих", now.minusSeconds(1), new PageRequest(0, 1, "createdAt,desc"));
    assertEquals(1, p.total());
    assertEquals(now, p.items().get(0).createdAt());
    assertEquals(0, comments.list(1, "%", null, new PageRequest(0, 10, "id,asc")).total());
  }

  @Test
  void deleteIsScopedToBook() {
    var c = comments.add(1, "A", "Text", Instant.now());
    assertFalse(comments.delete(2, c.id()));
    assertTrue(comments.findById(1, c.id()).isPresent());
    assertTrue(comments.delete(1, c.id()));
    assertFalse(comments.delete(1, c.id()));
  }

  @Test
  void reinitializationPreservesData() {
    comments.add(1, "A", "Text", Instant.now());
    db.initialize();
    assertEquals(6, books.search("", new PageRequest(0, 10, "id,asc")).total());
    assertEquals(1, comments.list(1, null, null, new PageRequest(0, 10, "id,asc")).total());
  }

  @Test
  void fileDatabaseSurvivesReopening(@TempDir Path temp) {
    String url = "jdbc:h2:file:" + temp.resolve("catalog").toAbsolutePath();
    var first = new Db(url);
    first.initialize();
    var c = new JdbcCommentRepository(first).add(1, "Михайло", "Збережено", Instant.now());
    var reopened = new Db(url);
    reopened.initialize();
    assertEquals(
        "Збережено", new JdbcCommentRepository(reopened).findById(1, c.id()).orElseThrow().text());
  }
}
