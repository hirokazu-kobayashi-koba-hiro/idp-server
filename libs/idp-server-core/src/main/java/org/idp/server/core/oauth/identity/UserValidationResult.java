package org.idp.server.core.oauth.identity;

import java.util.List;

public class UserValidationResult {

  boolean valid;
  List<String> errors;

  private UserValidationResult(boolean valid, List<String> errors) {
    this.valid = valid;
    this.errors = errors;
  }

  public static UserValidationResult success() {
    return new UserValidationResult(true, List.of());
  }

  public static UserValidationResult failure(List<String> errors) {
    return new UserValidationResult(false, errors);
  }

  public boolean isValid() {
    return valid;
  }

  public List<String> errors() {
    return errors;
  }
}
