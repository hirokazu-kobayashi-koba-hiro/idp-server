package org.idp.server.core.authentication.sms;

import org.idp.server.core.authentication.AuthorizationIdentifier;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

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
