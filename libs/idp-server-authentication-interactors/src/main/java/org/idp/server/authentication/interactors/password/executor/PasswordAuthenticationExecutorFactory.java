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

package org.idp.server.authentication.interactors.password.executor;

import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutor;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutorFactory;
import org.idp.server.core.openid.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.core.openid.identity.authentication.PasswordVerificationDelegation;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.datasource.cache.CacheStore;

public class PasswordAuthenticationExecutorFactory implements AuthenticationExecutorFactory {

  @Override
  public AuthenticationExecutor create(AuthenticationDependencyContainer container) {

    UserQueryRepository userQueryRepository = container.resolve(UserQueryRepository.class);
    PasswordVerificationDelegation passwordVerificationDelegation =
        container.resolve(PasswordVerificationDelegation.class);
    CacheStore cacheStore = container.resolve(CacheStore.class);

    return new PasswordAuthenticationExecutor(
        userQueryRepository, passwordVerificationDelegation, cacheStore);
  }
}
