package org.idp.server.core.identity.verification.verifier;

import org.idp.server.core.identity.User;
import org.idp.server.core.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.identity.verification.IdentityVerificationType;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplications;
import org.idp.server.core.identity.verification.application.IdentityVerificationRequest;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.tenant.Tenant;

import java.util.List;

public class DenyDuplicateIdentityVerificationApplicationVerifier
    implements IdentityVerificationRequestVerifier {

  @Override
  public boolean shouldVerify(
      Tenant tenant,
      User user,
      IdentityVerificationApplications applications,
      IdentityVerificationType type,
      IdentityVerificationProcess processes,
      IdentityVerificationRequest request,
      IdentityVerificationConfiguration verificationConfiguration) {

    return true;
  }

  @Override
  public IdentityVerificationRequestVerificationResult verify(
      Tenant tenant,
      User user,
      IdentityVerificationApplications applications,
      IdentityVerificationType type,
      IdentityVerificationProcess processes,
      IdentityVerificationRequest request,
      IdentityVerificationConfiguration verificationConfiguration) {

    if (applications.containsRunningState(type)) {
      List<String> errors = List.of("Duplicate application found for type " + type.name());
      return IdentityVerificationRequestVerificationResult.failure(errors);
    }

    return IdentityVerificationRequestVerificationResult.success();
  }
}
