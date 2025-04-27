package org.idp.server.core.identity.verification.verifier;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.identity.verification.IdentityVerificationType;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplications;
import org.idp.server.core.identity.verification.application.IdentityVerificationRequest;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.tenant.Tenant;

public class IdentityVerificationRequestVerifiers {

  List<IdentityVerificationRequestVerifier> verifiers;

  public IdentityVerificationRequestVerifiers() {
    this.verifiers = new ArrayList<>();
    this.verifiers.add(new DenyDuplicateIdentityVerificationApplicationVerifier());
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

      if (!verifier.shouldVerify(tenant, user, applications, type, processes, request, verificationConfiguration)) {
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
