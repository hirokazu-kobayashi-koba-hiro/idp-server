package org.idp.server.core.extension.identity.verification.verifier;

import java.util.List;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.IdentityVerificationRequest;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplications;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationProcessConfiguration;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

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

    IdentityVerificationProcessConfiguration processConfig =
        verificationConfiguration.getProcessConfig(processes);
    Map<String, Object> verificationSchema = processConfig.requestVerificationSchema();

    if (verificationSchema == null || verificationSchema.isEmpty()) {
      return false;
    }

    return verificationSchema.containsKey("duplicate_application")
        && (Boolean) verificationSchema.get("duplicate_application");
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
