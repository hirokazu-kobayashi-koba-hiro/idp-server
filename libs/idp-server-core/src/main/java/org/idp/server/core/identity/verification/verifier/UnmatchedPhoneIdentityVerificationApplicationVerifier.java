package org.idp.server.core.identity.verification.verifier;

import java.util.List;
import java.util.Map;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.identity.verification.IdentityVerificationRequest;
import org.idp.server.core.identity.verification.IdentityVerificationType;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplications;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationProcessConfiguration;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class UnmatchedPhoneIdentityVerificationApplicationVerifier implements IdentityVerificationRequestVerifier {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Override
  public boolean shouldVerify(Tenant tenant, User user, IdentityVerificationApplications applications, IdentityVerificationType type, IdentityVerificationProcess processes, IdentityVerificationRequest request, IdentityVerificationConfiguration verificationConfiguration) {

    IdentityVerificationProcessConfiguration processConfig = verificationConfiguration.getProcessConfig(processes);
    Map<String, Object> verificationSchema = processConfig.requestVerificationSchema();

    if (verificationSchema == null || verificationSchema.isEmpty()) {
      return false;
    }

    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromObject(verificationSchema);
    return jsonNodeWrapper.optValueAsBoolean("unmatched_user_claims_phone", false);
  }

  @Override
  public IdentityVerificationRequestVerificationResult verify(Tenant tenant, User user, IdentityVerificationApplications applications, IdentityVerificationType type, IdentityVerificationProcess processes, IdentityVerificationRequest request, IdentityVerificationConfiguration verificationConfiguration) {

    IdentityVerificationProcessConfiguration processConfig = verificationConfiguration.getProcessConfig(processes);
    Map<String, Object> verificationSchema = processConfig.requestVerificationSchema();
    JsonNodeWrapper jsonNodeWrapper = jsonConverter.readTree(verificationSchema);
    JsonNodeWrapper unmatchedUserClaims = jsonNodeWrapper.getValueAsJsonNode("unmatched_user_claims_phone");

    String property = unmatchedUserClaims.getValueOrEmptyAsString("property");
    String requestValue = request.optValueAsString(property, "");

    if (!requestValue.equals(user.phoneNumber())) {
      return IdentityVerificationRequestVerificationResult.failure(List.of("PhoneNumber does not match"));
    }

    return IdentityVerificationRequestVerificationResult.success();
  }
}
