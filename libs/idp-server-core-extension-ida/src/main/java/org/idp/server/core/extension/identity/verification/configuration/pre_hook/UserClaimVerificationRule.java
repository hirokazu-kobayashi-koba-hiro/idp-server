package org.idp.server.core.extension.identity.verification.configuration.pre_hook;

import org.idp.server.platform.json.JsonReadable;

public class UserClaimVerificationRule implements JsonReadable {
  String requestJsonPath;
  String userClaimJsonPath;

  public UserClaimVerificationRule() {}

  public UserClaimVerificationRule(String requestJsonPath, String userClaimJsonPath) {
    this.requestJsonPath = requestJsonPath;
    this.userClaimJsonPath = userClaimJsonPath;
  }

  public String requestJsonPath() {
    return requestJsonPath;
  }

  public String userClaimJsonPath() {
    return userClaimJsonPath;
  }
}
