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

package org.idp.server.federation.sso.oidc;

import org.idp.server.core.openid.federation.*;
import org.idp.server.core.openid.federation.plugin.FederationDependencyContainer;
import org.idp.server.core.openid.federation.plugin.FederationInteractorFactory;
import org.idp.server.core.openid.federation.repository.FederationConfigurationQueryRepository;
import org.idp.server.core.openid.federation.sso.SsoSessionCommandRepository;
import org.idp.server.core.openid.federation.sso.SsoSessionQueryRepository;

public class OidcFederationInteractorFactory implements FederationInteractorFactory {

  @Override
  public FederationType type() {
    return StandardSupportedFederationType.OIDC.toFederationType();
  }

  @Override
  public FederationInteractor create(FederationDependencyContainer container) {
    OidcSsoExecutors oidcSsoExecutors = container.resolve(OidcSsoExecutors.class);
    FederationConfigurationQueryRepository configurationQueryRepository =
        container.resolve(FederationConfigurationQueryRepository.class);
    SsoSessionCommandRepository sessionCommandRepository =
        container.resolve(SsoSessionCommandRepository.class);
    SsoSessionQueryRepository sessionQueryRepository =
        container.resolve(SsoSessionQueryRepository.class);
    return new OidcFederationInteractor(
        oidcSsoExecutors,
        configurationQueryRepository,
        sessionCommandRepository,
        sessionQueryRepository);
  }
}
