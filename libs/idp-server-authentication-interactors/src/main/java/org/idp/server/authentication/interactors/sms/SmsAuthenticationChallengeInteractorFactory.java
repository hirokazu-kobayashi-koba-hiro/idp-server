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

import org.idp.server.core.openid.authentication.AuthenticationInteractor;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutors;
import org.idp.server.core.openid.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.core.openid.authentication.plugin.AuthenticationInteractorFactory;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionCommandRepository;

public class SmsAuthenticationChallengeInteractorFactory
    implements AuthenticationInteractorFactory {

  @Override
  public AuthenticationInteractor create(AuthenticationDependencyContainer container) {

    AuthenticationExecutors executors = container.resolve(AuthenticationExecutors.class);
    AuthenticationInteractionCommandRepository interactionCommandRepository =
        container.resolve(AuthenticationInteractionCommandRepository.class);
    AuthenticationConfigurationQueryRepository configurationQueryRepository =
        container.resolve(AuthenticationConfigurationQueryRepository.class);
    return new SmsAuthenticationChallengeInteractor(
        executors, interactionCommandRepository, configurationQueryRepository);
  }
}
