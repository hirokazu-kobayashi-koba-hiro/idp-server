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

package org.idp.server.core.extension.identity.verification.application.pre_hook.additional_parameter;

import org.idp.server.core.openid.federation.sso.SsoCredentialsCommandRepository;
import org.idp.server.core.openid.federation.sso.SsoCredentialsQueryRepository;
import org.idp.server.platform.dependency.ApplicationComponentContainer;
import org.idp.server.platform.http.HttpRequestExecutor;

public class SsoCredentialsParameterResolverFactory
    implements AdditionalRequestParameterResolverFactory {

  @Override
  public AdditionalRequestParameterResolver create(ApplicationComponentContainer container) {
    SsoCredentialsQueryRepository ssoCredentialsQueryRepository =
        container.resolve(SsoCredentialsQueryRepository.class);
    SsoCredentialsCommandRepository ssoCredentialsCommandRepository =
        container.resolve(SsoCredentialsCommandRepository.class);
    HttpRequestExecutor httpRequestExecutor = container.resolve(HttpRequestExecutor.class);
    return new SsoCredentialsParameterResolver(
        ssoCredentialsQueryRepository, ssoCredentialsCommandRepository, httpRequestExecutor);
  }
}
