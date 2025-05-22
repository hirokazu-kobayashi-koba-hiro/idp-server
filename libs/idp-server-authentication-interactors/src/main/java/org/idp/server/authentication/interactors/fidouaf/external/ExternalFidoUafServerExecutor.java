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


package org.idp.server.authentication.interactors.fidouaf.external;

import java.util.Map;
import org.idp.server.authentication.interactors.fidouaf.*;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.oauth.OAuthAuthorizationConfiguration;
import org.idp.server.core.oidc.authentication.AuthorizationIdentifier;
import org.idp.server.core.oidc.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class ExternalFidoUafServerExecutor implements FidoUafExecutor {

  AuthenticationConfigurationQueryRepository configurationQueryRepository;
  ExternalFidoUafServerHttpClient httpClient;
  JsonConverter jsonConverter;

  public ExternalFidoUafServerExecutor(
      AuthenticationConfigurationQueryRepository configurationQueryRepository) {
    this.configurationQueryRepository = configurationQueryRepository;
    this.httpClient = new ExternalFidoUafServerHttpClient();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public FidoUafExecutorType type() {
    return new FidoUafExecutorType("external");
  }

  @Override
  public FidoUafExecutionResult getFidoUafFacets(
      Tenant tenant, FidoUafConfiguration configuration) {

    return execute("facets", FidoUafExecutionRequest.empty(), configuration);
  }

  @Override
  public FidoUafExecutionResult challengeRegistration(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      FidoUafExecutionRequest request,
      FidoUafConfiguration configuration) {

    return execute("registration-challenge", request, configuration);
  }

  @Override
  public FidoUafExecutionResult verifyRegistration(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      FidoUafExecutionRequest request,
      FidoUafConfiguration configuration) {

    return execute("registration", request, configuration);
  }

  @Override
  public FidoUafExecutionResult challengeAuthentication(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      FidoUafExecutionRequest request,
      FidoUafConfiguration configuration) {

    return execute("authentication-challenge", request, configuration);
  }

  @Override
  public FidoUafExecutionResult verifyAuthentication(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      FidoUafExecutionRequest request,
      FidoUafConfiguration configuration) {

    return execute("authentication", request, configuration);
  }

  @Override
  public FidoUafExecutionResult deleteKey(
      Tenant tenant, FidoUafExecutionRequest request, FidoUafConfiguration fidoUafConfiguration) {
    return execute("delete-key", request, fidoUafConfiguration);
  }

  private FidoUafExecutionResult execute(
      String executionType, FidoUafExecutionRequest request, FidoUafConfiguration configuration) {

    Map<String, Object> detail = configuration.getDetail(type());
    ExternalFidoUafServerConfiguration externalFidoUafServerConfiguration =
        jsonConverter.read(detail, ExternalFidoUafServerConfiguration.class);

    ExternalFidoUafServerExecutionConfiguration executionConfiguration =
        externalFidoUafServerConfiguration.getExecutionConfig(executionType);
    OAuthAuthorizationConfiguration oAuthAuthorizationConfiguration =
        externalFidoUafServerConfiguration.oauthAuthorization();

    ExternalFidoUafServerHttpRequestResult httpRequestResult =
        httpClient.execute(request, executionConfiguration, oAuthAuthorizationConfiguration);

    if (httpRequestResult.isClientError()) {
      return FidoUafExecutionResult.clientError(httpRequestResult.responseBody());
    }

    if (httpRequestResult.isServerError()) {
      return FidoUafExecutionResult.serverError(httpRequestResult.responseBody());
    }

    return FidoUafExecutionResult.success(httpRequestResult.responseBody());
  }
}
