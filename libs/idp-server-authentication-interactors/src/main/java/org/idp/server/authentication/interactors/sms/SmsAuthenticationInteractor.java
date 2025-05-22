/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.authentication.interactors.sms;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.oidc.authentication.*;
import org.idp.server.core.oidc.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.oidc.identity.repository.UserQueryRepository;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

public class SmsAuthenticationInteractor implements AuthenticationInteractor {

  SmsAuthenticationExecutors executors;
  AuthenticationConfigurationQueryRepository configurationQueryRepository;

  public SmsAuthenticationInteractor(
      SmsAuthenticationExecutors executors,
      AuthenticationConfigurationQueryRepository configurationQueryRepository) {
    this.executors = executors;
    this.configurationQueryRepository = configurationQueryRepository;
  }

  @Override
  public AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      AuthenticationTransaction transaction,
      UserQueryRepository userQueryRepository) {
    SmsAuthenticationConfiguration configuration =
        configurationQueryRepository.get(tenant, "sms", SmsAuthenticationConfiguration.class);
    SmsAuthenticationExecutor executor = executors.get(configuration.type());

    SmsAuthenticationExecutionRequest executionRequest =
        new SmsAuthenticationExecutionRequest(request.toMap());
    SmsAuthenticationExecutionResult executionResult =
        executor.verify(tenant, authorizationIdentifier, executionRequest, configuration);

    if (executionResult.isClientError()) {
      return AuthenticationInteractionRequestResult.clientError(
          executionResult.contents(), type, DefaultSecurityEventType.sms_verification_failure);
    }

    if (executionResult.isServerError()) {
      return AuthenticationInteractionRequestResult.serverError(
          executionResult.contents(), type, DefaultSecurityEventType.sms_verification_failure);
    }

    Authentication authentication =
        new Authentication()
            .setTime(SystemDateTime.now())
            .addMethods(new ArrayList<>(List.of("opt")))
            .addAcrValues(List.of("urn:mace:incommon:iap:silver"));

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        transaction.user(),
        authentication,
        executionResult.contents(),
        DefaultSecurityEventType.sms_verification_success);
  }
}
