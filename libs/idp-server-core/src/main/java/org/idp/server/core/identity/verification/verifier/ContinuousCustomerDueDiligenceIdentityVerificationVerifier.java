package org.idp.server.core.identity.verification.verifier;

import java.util.List;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.identity.verification.IdentityVerificationRequest;
import org.idp.server.core.identity.verification.IdentityVerificationType;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplications;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class ContinuousCustomerDueDiligenceIdentityVerificationVerifier implements IdentityVerificationRequestVerifier {

  public boolean shouldVerify(Tenant tenant, User user, IdentityVerificationApplications applications, IdentityVerificationType type, IdentityVerificationProcess processes, IdentityVerificationRequest request, IdentityVerificationConfiguration verificationConfiguration) {

    return type.isContinuousCustomerDueDiligence();
  }

  @Override
  public IdentityVerificationRequestVerificationResult verify(Tenant tenant, User user, IdentityVerificationApplications applications, IdentityVerificationType type, IdentityVerificationProcess processes, IdentityVerificationRequest request, IdentityVerificationConfiguration verificationConfiguration) {

    if (!applications.containsApproved(verificationConfiguration.approvedTargetTypes())) {

      List<String> errors = List.of(String.format("user does not have approved application required any type (%s)", verificationConfiguration.approvedTargetTypesAsString()));
      return IdentityVerificationRequestVerificationResult.failure(errors);
    }

    return IdentityVerificationRequestVerificationResult.success();
  }
}
