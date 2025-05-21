package org.idp.server.core.identity.verification.delegation.request;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.identity.verification.IdentityVerificationRequest;
import org.idp.server.core.identity.verification.IdentityVerificationType;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplication;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplications;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class ContinuousCustomerDueDiligenceParameterResolver
    implements AdditionalRequestParameterResolver {

  public boolean shouldResolve(
      Tenant tenant,
      User user,
      IdentityVerificationApplications applications,
      IdentityVerificationType type,
      IdentityVerificationProcess processes,
      IdentityVerificationRequest request,
      IdentityVerificationConfiguration verificationConfiguration) {

    return type.isContinuousCustomerDueDiligence();
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

    List<Map<String, Object>> applicationList = new ArrayList<>();
    for (IdentityVerificationApplication application : applications) {
      Map<String, Object> applicationMap = new HashMap<>();
      applicationMap.put(
          verificationConfiguration.externalWorkflowApplicationIdParam().value(),
          application.externalApplicationId().value());
      applicationMap.put("application_type", application.identityVerificationType().name());
      applicationList.add(applicationMap);
    }

    additionalParameters.put("relation_applications", applicationList);

    return additionalParameters;
  }
}
