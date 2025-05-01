package org.idp.server.core.identity.verification.verifier;

import java.util.List;
import java.util.Map;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.identity.verification.IdentityVerificationRequest;
import org.idp.server.core.identity.verification.IdentityVerificationType;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplications;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationProcessConfiguration;
import org.idp.server.core.tenant.Tenant;

public class UnmatchedEmailIdentityVerificationApplicationVerifier
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

    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromObject(verificationSchema);
    return jsonNodeWrapper.optValueAsBoolean("unmatched_user_claims_email", false);
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

    IdentityVerificationProcessConfiguration processConfig =
        verificationConfiguration.getProcessConfig(processes);
    Map<String, Object> verificationSchema = processConfig.requestVerificationSchema();
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromObject(verificationSchema);
    JsonNodeWrapper unmatchedUserClaims =
        jsonNodeWrapper.getValueAsJsonNode("unmatched_user_claims_email");

    String property = unmatchedUserClaims.getValueOrEmptyAsString("property");
    String requestValue = request.optValueAsString(property, "");

    if (!requestValue.equals(user.email())) {
      return IdentityVerificationRequestVerificationResult.failure(List.of("Email does not match"));
    }

    return IdentityVerificationRequestVerificationResult.success();
  }
}
