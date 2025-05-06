package org.idp.server.core.authentication;

import java.util.Objects;

public class AuthenticationInteractionResult {

  String type;
  int callCount;
  int successCount;
  int failureCount;

  public AuthenticationInteractionResult() {}

  public AuthenticationInteractionResult(String type, int callCount, int successCount, int failureCount) {
    this.type = type;
    this.callCount = callCount;
    this.successCount = successCount;
    this.failureCount = failureCount;
  }

  public String type() {
    return type;
  }

  public int callCount() {
    return callCount;
  }

  public int successCount() {
    return successCount;
  }

  public int failureCount() {
    return failureCount;
  }

  public AuthenticationInteractionResult update(AuthenticationInteractionRequestResult interactionRequestResult) {
    int increaseSuccessCount = interactionRequestResult.isSuccess() ? 1 : 0;
    int increaseFailureCount = interactionRequestResult.isSuccess() ? 0 : 1;

    return new AuthenticationInteractionResult(type, callCount + 1, successCount + increaseSuccessCount, failureCount + increaseFailureCount);
  }

  public boolean exists() {
    return type != null && !type.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass())
      return false;
    AuthenticationInteractionResult that = (AuthenticationInteractionResult) o;
    return Objects.equals(type, that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(type);
  }
}
