package org.idp.server.core.identity.verification.result;

import org.idp.server.core.identity.verification.IdentityVerificationRequest;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplication;

public class IdentityVerificationResult {

  public static IdentityVerificationResult create(
      IdentityVerificationApplication updatedExamination, IdentityVerificationRequest request) {

    return new IdentityVerificationResult();
  }
}
