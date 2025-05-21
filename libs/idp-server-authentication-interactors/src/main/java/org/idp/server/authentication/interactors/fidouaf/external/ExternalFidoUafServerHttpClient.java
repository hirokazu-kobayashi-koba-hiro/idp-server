package org.idp.server.authentication.interactors.fidouaf.external;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.http.*;
import org.idp.server.basic.oauth.OAuthAuthorizationConfiguration;
import org.idp.server.basic.oauth.OAuthAuthorizationResolver;
import org.idp.server.basic.oauth.OAuthAuthorizationResolvers;
import org.idp.server.authentication.interactors.fidouaf.FidoUafExecutionRequest;

public class ExternalFidoUafServerHttpClient {

  OAuthAuthorizationResolvers authorizationResolvers;
  HttpRequestExecutor httpRequestExecutor;

  public ExternalFidoUafServerHttpClient() {
    this.authorizationResolvers = new OAuthAuthorizationResolvers();
    this.httpRequestExecutor = new HttpRequestExecutor(HttpClientFactory.defaultClient());
  }

  public ExternalFidoUafServerHttpRequestResult execute(
      FidoUafExecutionRequest request,
      ExternalFidoUafServerExecutionConfiguration configuration,
      OAuthAuthorizationConfiguration oAuthAuthorizationConfig) {

    HttpRequestHeaders httpRequestHeaders =
        createHttpRequestHeaders(configuration.httpRequestHeaders(), oAuthAuthorizationConfig);

    HttpRequestResult executionResult =
        httpRequestExecutor.execute(
            configuration.httpRequestUrl(),
            configuration.httpMethod(),
            httpRequestHeaders,
            new HttpRequestBaseParams(request.toMap()),
            configuration.httpRequestDynamicBodyKeys(),
            configuration.httpRequestStaticBody());

    return new ExternalFidoUafServerHttpRequestResult(executionResult);
  }

  private HttpRequestHeaders createHttpRequestHeaders(
      HttpRequestHeaders httpRequestHeaders,
      OAuthAuthorizationConfiguration oAuthAuthorizationConfig) {
    Map<String, String> values = new HashMap<>(httpRequestHeaders.toMap());

    if (oAuthAuthorizationConfig.exists()) {
      OAuthAuthorizationResolver resolver =
          authorizationResolvers.get(oAuthAuthorizationConfig.type());
      String accessToken = resolver.resolve(oAuthAuthorizationConfig);
      values.put("Authorization", "Bearer " + accessToken);
    }

    return new HttpRequestHeaders(values);
  }
}
