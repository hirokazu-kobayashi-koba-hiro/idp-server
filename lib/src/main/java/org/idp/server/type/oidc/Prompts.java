package org.idp.server.type.oidc;

import java.util.*;

/**
 * prompt OPTIONAL. Space delimited,
 *
 * <p>case sensitive list of ASCII string values that specifies whether the Authorization Server
 * prompts the End-User for reauthentication and consent.
 */
public class Prompts implements Iterable<Prompt> {

  List<Prompt> values;

  public Prompts() {
    this.values = new ArrayList<>();
  }

  public Prompts(List<Prompt> values) {
    this.values = values;
  }

  @Override
  public Iterator<Prompt> iterator() {
    return values.iterator();
  }

  public static Prompts of(String value) {
    if (Objects.isNull(value) || value.isEmpty()) {
      return new Prompts();
    }
    List<Prompt> values = Arrays.stream(value.split(" ")).map(Prompt::of).toList();
    return new Prompts(values);
  }

  public boolean hasNone() {
    return values.contains(Prompt.none);
  }

  public boolean hasUnknown() {
    return values.contains(Prompt.unknown);
  }

  public boolean isMultiValue() {
    return values.size() >= 2;
  }
}
