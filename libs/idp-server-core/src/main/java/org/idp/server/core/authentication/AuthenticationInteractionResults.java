package org.idp.server.core.authentication;

import java.util.*;
import java.util.stream.Collectors;
import org.idp.server.basic.exception.NotFoundException;

public class AuthenticationInteractionResults implements Iterable<AuthenticationInteractionResult> {

  Set<AuthenticationInteractionResult> values;

  public AuthenticationInteractionResults() {
    this.values = new HashSet<>();
  }

  public AuthenticationInteractionResults(Set<AuthenticationInteractionResult> values) {
    this.values = values;
  }

  @Override
  public Iterator<AuthenticationInteractionResult> iterator() {
    return values.iterator();
  }

  public AuthenticationInteractionResults filter(String authenticationType) {
    Set<AuthenticationInteractionResult> filtered = values.stream().filter(result -> result.type().equals(authenticationType)).collect(Collectors.toSet());
    return new AuthenticationInteractionResults(filtered);
  }

  public boolean containsSuccessful(String type) {
    return values.stream().filter(result -> result.type().equals(type)).anyMatch(result -> result.successCount() > 0);
  }

  public boolean allSuccess() {
    return values.stream().allMatch(result -> result.successCount() > 0);
  }

  public boolean containsAnySuccess() {
    return values.stream().anyMatch(result -> result.successCount() > 0);
  }

  public boolean contains(String type) {
    return values.stream().anyMatch(result -> result.type().equals(type));
  }

  public boolean exists() {
    return values != null && !values.isEmpty();
  }

  public Set<AuthenticationInteractionResult> toSet() {
    return values;
  }

  public AuthenticationInteractionResult get(String interactionType) {
    return values.stream().filter(result -> result.type().equals(interactionType)).findFirst().orElseThrow(() -> new NotFoundException(String.format("No interaction result found (%s)", interactionType)));
  }
}
