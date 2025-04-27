package org.idp.server.core.identity.trustframework;

import org.idp.server.core.identity.trustframework.application.IdentityVerificationRequest;

public class IdentityVerificationResult {

  public static IdentityVerificationResult create(
      IdentityVerificationApplication updatedExamination, IdentityVerificationRequest request) {

    return new IdentityVerificationResult();
  }
}
