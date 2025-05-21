package org.idp.server.core.extension.identity.verification.delegation.request;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.identity.User;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.IdentityVerificationRequest;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplications;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class AdditionalRequestParameterResolvers {

  List<AdditionalRequestParameterResolver> resolvers;

  public AdditionalRequestParameterResolvers() {
    this.resolvers = new ArrayList<>();
    this.resolvers.add(new UserIdParameterResolver());
    this.resolvers.add(new ContinuousCustomerDueDiligenceParameterResolver());
  }

  public Map<String, Object> resolve(
      Tenant tenant,
      User user,
      IdentityVerificationApplications applications,
      IdentityVerificationType type,
      IdentityVerificationProcess processes,
      IdentityVerificationRequest request,
      IdentityVerificationConfiguration verificationConfiguration) {

    Map<String, Object> additionalParameters = new HashMap<>();

    for (AdditionalRequestParameterResolver resolver : resolvers) {
      if (resolver.shouldResolve(
          tenant, user, applications, type, processes, request, verificationConfiguration)) {
        additionalParameters.putAll(
            resolver.resolve(
                tenant, user, applications, type, processes, request, verificationConfiguration));
      }
    }

    return additionalParameters;
  }
}
