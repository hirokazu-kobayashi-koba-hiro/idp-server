package org.idp.server.core.extension.identity.verification.configuration.pre_hook;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.platform.json.JsonReadable;

public class UserClaimPreHook implements JsonReadable {

  List<UserClaimVerificationRule> verificationParameters;

  public UserClaimPreHook() {
    this.verificationParameters = new ArrayList<>();
  }

  public UserClaimPreHook(List<UserClaimVerificationRule> verificationParameters) {
    this.verificationParameters = verificationParameters;
  }

  public List<UserClaimVerificationRule> verificationParameters() {
    if (verificationParameters == null) {
      return new ArrayList<>();
    }
    return verificationParameters;
  }
}
