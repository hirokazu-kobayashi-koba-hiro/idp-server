package org.idp.server.core.authentication;

public class AuthenticationInteractionResult {

  int callCount;
  int successCount;
  int failureCount;

  public AuthenticationInteractionResult() {}

  public AuthenticationInteractionResult(int callCount, int successCount, int failureCount) {
    this.callCount = callCount;
    this.successCount = successCount;
    this.failureCount = failureCount;
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

  public AuthenticationInteractionResult updateWith(
      AuthenticationInteractionRequestResult interactionRequestResult) {
    int increaseSuccessCount = interactionRequestResult.isSuccess() ? 1 : 0;
    int increaseFailureCount = interactionRequestResult.isSuccess() ? 0 : 1;

    return new AuthenticationInteractionResult(
        callCount + 1, successCount + increaseSuccessCount, failureCount + increaseFailureCount);
  }
}
