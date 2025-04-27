package org.idp.server.core.identity.verification;

import org.idp.server.core.identity.verification.application.IdentityVerificationApplication;
import org.idp.server.core.identity.verification.application.IdentityVerificationRequest;

public class IdentityVerificationResult {

  public static IdentityVerificationResult create(
      IdentityVerificationApplication updatedExamination, IdentityVerificationRequest request) {

    return new IdentityVerificationResult();
  }
}
