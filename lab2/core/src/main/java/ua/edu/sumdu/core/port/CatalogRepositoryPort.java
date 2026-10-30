package ua.edu.sumdu.core.port;

import java.util.Optional;
import ua.edu.sumdu.core.domain.*;

public interface CatalogRepositoryPort {
  Page<Book> search(String query, PageRequest request);

  Optional<Book> findById(long id);

  Book add(String title, String author, int pubYear);
}
