package org.idp.server.core.identity.verification.verifier;

import org.idp.server.core.identity.User;
import org.idp.server.core.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.identity.verification.IdentityVerificationRequest;
import org.idp.server.core.identity.verification.IdentityVerificationType;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplications;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface IdentityVerificationRequestVerifier {

  default boolean shouldVerify(
      Tenant tenant,
      User user,
      IdentityVerificationApplications applications,
      IdentityVerificationType type,
      IdentityVerificationProcess processes,
      IdentityVerificationRequest request,
      IdentityVerificationConfiguration verificationConfiguration) {
    return false;
  }

  IdentityVerificationRequestVerificationResult verify(
      Tenant tenant,
      User user,
      IdentityVerificationApplications applications,
      IdentityVerificationType type,
      IdentityVerificationProcess processes,
      IdentityVerificationRequest request,
      IdentityVerificationConfiguration verificationConfiguration);
}
