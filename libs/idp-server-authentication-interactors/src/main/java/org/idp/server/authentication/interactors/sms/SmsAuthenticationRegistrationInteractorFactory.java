/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.idp.server.authentication.interactors.sms;

import org.idp.server.core.oidc.authentication.AuthenticationInteractionType;
import org.idp.server.core.oidc.authentication.AuthenticationInteractor;
import org.idp.server.core.oidc.authentication.StandardAuthenticationInteraction;
import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.core.oidc.authentication.plugin.AuthenticationInteractorFactory;
import org.idp.server.core.oidc.authentication.repository.AuthenticationConfigurationQueryRepository;

public class SmsAuthenticationRegistrationInteractorFactory
    implements AuthenticationInteractorFactory {

  @Override
  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.SMS_AUTHENTICATION_REGISTRATION.toType();
  }

  @Override
  public AuthenticationInteractor create(AuthenticationDependencyContainer container) {

    SmsAuthenticationExecutors executors = container.resolve(SmsAuthenticationExecutors.class);
    AuthenticationConfigurationQueryRepository configurationQueryRepository =
        container.resolve(AuthenticationConfigurationQueryRepository.class);
    return new SmsAuthenticationRegistrationInteractor(executors, configurationQueryRepository);
  }
}
