package org.idp.server.authentication.interactors.sms;

import org.idp.server.core.oidc.authentication.AuthorizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface SmsAuthenticationExecutor {

  SmsAuthenticationType type();

  SmsAuthenticationExecutionResult challenge(
      Tenant tenant,
      AuthorizationIdentifier identifier,
      SmsAuthenticationExecutionRequest request,
      SmsAuthenticationConfiguration configuration);

  SmsAuthenticationExecutionResult verify(
      Tenant tenant,
      AuthorizationIdentifier identifier,
      SmsAuthenticationExecutionRequest request,
      SmsAuthenticationConfiguration configuration);
}
