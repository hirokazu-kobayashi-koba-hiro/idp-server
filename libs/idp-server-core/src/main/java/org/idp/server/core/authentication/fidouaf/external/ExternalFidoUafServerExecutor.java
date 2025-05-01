package org.idp.server.core.authentication.fidouaf.external;

import java.util.Map;
import org.idp.server.core.authentication.*;
import org.idp.server.core.authentication.fidouaf.*;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.oauth.OAuthAuthorizationConfiguration;
import org.idp.server.core.tenant.Tenant;

public class ExternalFidoUafServerExecutor implements FidoUafExecutor {

  AuthenticationConfigurationQueryRepository configurationQueryRepository;
  ExternalFidoUafServerHttpClient httpClient;
  JsonConverter jsonConverter;

  public ExternalFidoUafServerExecutor(
      AuthenticationConfigurationQueryRepository configurationQueryRepository) {
    this.configurationQueryRepository = configurationQueryRepository;
    this.httpClient = new ExternalFidoUafServerHttpClient();
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
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
