package org.idp.server.control_plane.management.user.verifier;

import org.idp.server.control_plane.base.verifier.UserVerifier;
import org.idp.server.control_plane.base.verifier.VerificationResult;
import org.idp.server.control_plane.management.user.UserRegistrationContext;

public class UserRegistrationVerifier {

  UserVerifier userVerifier;

  public UserRegistrationVerifier(UserVerifier userVerifier) {
    this.userVerifier = userVerifier;
  }

  public UserRegistrationVerificationResult verify(UserRegistrationContext context) {

    VerificationResult verificationResult = userVerifier.verify(context.tenant(), context.user());

    if (!verificationResult.isValid()) {
      return UserRegistrationVerificationResult.error(verificationResult, context.isDryRun());
    }

    return UserRegistrationVerificationResult.success(verificationResult, context.isDryRun());
  }
}
