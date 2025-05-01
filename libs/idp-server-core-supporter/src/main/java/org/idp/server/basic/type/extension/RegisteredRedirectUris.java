package org.idp.server.basic.type.extension;

import java.util.Iterator;
import java.util.List;
import org.idp.server.basic.http.UriMatcher;

public class RegisteredRedirectUris implements Iterable<String> {
  List<String> values;

  public RegisteredRedirectUris(List<String> values) {
    this.values = values;
  }

  public boolean contains(String other) {
    return values.contains(other);
  }

  public boolean containsWithNormalizationAndComparison(String other) {
    return values.stream()
        .anyMatch(value -> UriMatcher.matchWithNormalizationAndComparison(value, other));
  }

  @Override
  public Iterator<String> iterator() {
    return values.iterator();
  }
}
