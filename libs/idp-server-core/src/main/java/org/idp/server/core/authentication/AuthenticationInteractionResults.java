package org.idp.server.core.authentication;

import java.util.*;
import org.idp.server.basic.json.JsonReadable;

public class AuthenticationInteractionResults implements JsonReadable {

  Map<String, AuthenticationInteractionResult> values;

  public AuthenticationInteractionResults() {
    this.values = new HashMap<>();
  }

  public AuthenticationInteractionResults(Map<String, AuthenticationInteractionResult> values) {
    this.values = values;
  }

  public boolean containsSuccessful(String type) {
    if (!values.containsKey(type)) {
      return false;
    }
    AuthenticationInteractionResult result = values.get(type);
    return result.successCount() > 0;
  }

  public boolean contains(String type) {
    return values.containsKey(type);
  }

  public boolean exists() {
    return values != null && !values.isEmpty();
  }

  public Map<String, AuthenticationInteractionResult> toMap() {
    return values;
  }

  public AuthenticationInteractionResult get(String interactionType) {
    return values.get(interactionType);
  }

  public boolean containsAnySuccess() {
    for (AuthenticationInteractionResult result : values.values()) {
      if (result.successCount() > 0) {
        return true;
      }
    }
    return false;
  }
}
