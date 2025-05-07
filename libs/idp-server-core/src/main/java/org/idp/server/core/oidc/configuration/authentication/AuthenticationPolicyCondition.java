package org.idp.server.core.oidc.configuration.authentication;

import org.idp.server.basic.json.JsonReadable;

public class AuthenticationPolicyCondition implements JsonReadable {
  String type;
  int successCount;
  int failureCount;

  public AuthenticationPolicyCondition() {}

  public AuthenticationPolicyCondition(String type, int successCount, int failureCount) {
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
