package org.idp.server.core.identity.verification.delegation.request;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.identity.verification.IdentityVerificationRequest;
import org.idp.server.core.identity.verification.IdentityVerificationType;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplications;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationProcessConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class UserIdParameterResolver implements AdditionalRequestParameterResolver {

  public boolean shouldResolve(
      Tenant tenant,
      User user,
      IdentityVerificationApplications applications,
      IdentityVerificationType type,
      IdentityVerificationProcess processes,
      IdentityVerificationRequest request,
      IdentityVerificationConfiguration verificationConfiguration) {
    IdentityVerificationProcessConfiguration processConfig =
        verificationConfiguration.getProcessConfig(processes);
    Map<String, Object> additionalParameterSchema =
        processConfig.requestAdditionalParameterSchema();

    if (additionalParameterSchema == null || additionalParameterSchema.isEmpty()) {
      return false;
    }

    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromObject(additionalParameterSchema);
    return jsonNodeWrapper.optValueAsBoolean("user_id", false);
  }

  @Override
  public Map<String, Object> resolve(
      Tenant tenant,
      User user,
      IdentityVerificationApplications applications,
      IdentityVerificationType type,
      IdentityVerificationProcess processes,
      IdentityVerificationRequest request,
      IdentityVerificationConfiguration verificationConfiguration) {
    Map<String, Object> additionalParameters = new HashMap<>();
    additionalParameters.put("user_id", user.sub());
    String providerUserId = user.providerUserId();
    if (user.hasProviderUserId()) {
      additionalParameters.put("provider_user_id", providerUserId);
    }

    return additionalParameters;
  }
}
