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

package org.idp.server.core.adapters.datasource.federation.credentials;

import org.idp.server.core.adapters.datasource.federation.credentials.query.SsoCredentialsQueryDataSource;
import org.idp.server.core.adapters.datasource.federation.credentials.query.SsoCredentialsSqlExecutor;
import org.idp.server.core.adapters.datasource.federation.credentials.query.SsoCredentialsSqlExecutors;
import org.idp.server.core.openid.federation.sso.SsoCredentialsQueryRepository;
import org.idp.server.core.openid.token.plugin.AccessTokenCustomClaimsCreator;
import org.idp.server.core.openid.token.plugin.AccessTokenCustomClaimsCreatorFactory;
import org.idp.server.core.openid.token.plugin.SsoCredentialsReferenceClaimsCreator;
import org.idp.server.platform.datasource.ApplicationDatabaseTypeProvider;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.json.JsonConverter;

public class SsoCredentialsReferenceClaimsCreatorFactory
    implements AccessTokenCustomClaimsCreatorFactory {

  @Override
  public AccessTokenCustomClaimsCreator create(ApplicationComponentDependencyContainer container) {
    ApplicationDatabaseTypeProvider databaseTypeProvider =
        container.resolve(ApplicationDatabaseTypeProvider.class);
    SsoCredentialsSqlExecutors executors = new SsoCredentialsSqlExecutors();
    SsoCredentialsSqlExecutor executor = executors.get(databaseTypeProvider.provide());
    JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
    SsoCredentialsQueryRepository ssoCredentialsQueryRepository =
        new SsoCredentialsQueryDataSource(executor, jsonConverter);
    return new SsoCredentialsReferenceClaimsCreator(ssoCredentialsQueryRepository);
  }

  @Override
  public String type() {
    return "sso_credentials_reference";
  }
}
