package org.idp.server.core.identity.trustframework.delegation;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.basic.http.*;
import org.idp.server.core.identity.trustframework.application.IdentityVerificationRequest;
import org.idp.server.core.identity.trustframework.configuration.IdentityVerificationOAuthAuthorizationConfiguration;
import org.idp.server.core.identity.trustframework.configuration.IdentityVerificationProcessConfiguration;
import org.idp.server.core.type.oauth.AccessTokenEntity;

public class ExternalWorkflowDelegationClient {

  OAuthAuthorizationResolvers authorizationResolvers;
  HttpRequestExecutor httpRequestExecutor;

  public ExternalWorkflowDelegationClient() {
    this.authorizationResolvers = new OAuthAuthorizationResolvers();
    this.httpRequestExecutor = new HttpRequestExecutor(HttpClientFactory.defaultClient());
  }

  public ExternalWorkflowApplyingResult execute(
      IdentityVerificationRequest request,
      IdentityVerificationProcessConfiguration processConfig,
      IdentityVerificationOAuthAuthorizationConfiguration oAuthAuthorizationConfig) {

    HttpRequestHeaders httpRequestHeaders =
        createHttpRequestHeaders(processConfig.httpRequestHeaders(), oAuthAuthorizationConfig);

    HttpRequestResult executionResult =
        httpRequestExecutor.execute(
            processConfig.httpRequestUrl(),
            processConfig.httpMethod(),
            httpRequestHeaders,
            new HttpRequestBaseParams(request.toMap()),
            processConfig.httpRequestDynamicBodyKeys(),
            processConfig.httpRequestStaticBody());

    return new ExternalWorkflowApplyingResult(executionResult);
  }

  public HttpRequestHeaders createHttpRequestHeaders(
      HttpRequestHeaders httpRequestHeaders,
      IdentityVerificationOAuthAuthorizationConfiguration oAuthAuthorizationConfig) {
    Map<String, String> values = new HashMap<>(httpRequestHeaders.toMap());

    if (oAuthAuthorizationConfig.exists()) {
      OAuthAuthorizationResolver resolver =
          authorizationResolvers.get(oAuthAuthorizationConfig.type());
      AccessTokenEntity accessTokenEntity = resolver.resolve(oAuthAuthorizationConfig);
      values.put("Authorization", "Bearer " + accessTokenEntity.value());
    }

    return new HttpRequestHeaders(values);
  }
}
