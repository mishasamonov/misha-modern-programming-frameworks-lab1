package ua.edu.sumdu.core.domain;

import java.util.List;

public record Page<T>(List<T> items, PageRequest request, long total) {
  public Page {
    items = List.copyOf(items);
  }

  public long totalPages() {
    return (total + request.size() - 1) / request.size();
  }
}
