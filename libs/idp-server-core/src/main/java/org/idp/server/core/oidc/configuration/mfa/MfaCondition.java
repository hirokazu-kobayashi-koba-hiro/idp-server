package org.idp.server.core.oidc.configuration.mfa;

import org.idp.server.basic.json.JsonReadable;

public class MfaCondition implements JsonReadable {
  String type;
  int successCount;
  int failureCount;

  public MfaCondition() {}

  public MfaCondition(String type, int successCount, int failureCount) {
    this.type = type;
    this.successCount = successCount;
    this.failureCount = failureCount;
  }

  public String type() {
    return type;
  }

  public int successCount() {
    return successCount;
  }

  public int failureCount() {
    return failureCount;
  }

  public boolean isSatisfiedSuccess(int successCount) {
    return successCount >= this.successCount;
  }

  public boolean isSatisfiedFailure(int failureCount) {
    return failureCount >= this.failureCount;
  }
}
