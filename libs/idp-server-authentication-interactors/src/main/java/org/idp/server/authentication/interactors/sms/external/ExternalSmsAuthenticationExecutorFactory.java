/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.authentication.interactors.sms.external;

import org.idp.server.authentication.interactors.sms.SmsAuthenticationExecutor;
import org.idp.server.authentication.interactors.sms.SmsAuthenticationExecutorFactory;
import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.core.oidc.authentication.repository.AuthenticationInteractionCommandRepository;
import org.idp.server.core.oidc.authentication.repository.AuthenticationInteractionQueryRepository;

public class ExternalSmsAuthenticationExecutorFactory implements SmsAuthenticationExecutorFactory {

  @Override
  public SmsAuthenticationExecutor create(AuthenticationDependencyContainer container) {

    AuthenticationInteractionCommandRepository interactionCommandRepository =
        container.resolve(AuthenticationInteractionCommandRepository.class);
    AuthenticationInteractionQueryRepository interactionQueryRepository =
        container.resolve(AuthenticationInteractionQueryRepository.class);
    return new ExternalSmsAuthenticationExecutor(
        interactionCommandRepository, interactionQueryRepository);
  }
}
