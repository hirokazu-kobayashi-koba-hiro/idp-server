package org.idp.server.core.extension.identity.verification.verifier;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.IdentityVerificationRequest;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplications;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class IdentityVerificationRequestVerifiers {

  List<IdentityVerificationRequestVerifier> verifiers;

  public IdentityVerificationRequestVerifiers() {
    this.verifiers = new ArrayList<>();
    this.verifiers.add(new DenyDuplicateIdentityVerificationApplicationVerifier());
    this.verifiers.add(new UnmatchedEmailIdentityVerificationApplicationVerifier());
    this.verifiers.add(new UnmatchedPhoneIdentityVerificationApplicationVerifier());
    this.verifiers.add(new ContinuousCustomerDueDiligenceIdentityVerificationVerifier());
  }

  public IdentityVerificationRequestVerificationResult verify(
      Tenant tenant,
      User user,
      IdentityVerificationApplications applications,
      IdentityVerificationType type,
      IdentityVerificationProcess processes,
      IdentityVerificationRequest request,
      IdentityVerificationConfiguration verificationConfiguration) {

    for (IdentityVerificationRequestVerifier verifier : verifiers) {

      if (!verifier.shouldVerify(
          tenant, user, applications, type, processes, request, verificationConfiguration)) {
        continue;
      }

      IdentityVerificationRequestVerificationResult verifyResult =
          verifier.verify(
              tenant, user, applications, type, processes, request, verificationConfiguration);

      if (verifyResult.isError()) {
        return verifyResult;
      }
    }

    return IdentityVerificationRequestVerificationResult.success();
  }
}
