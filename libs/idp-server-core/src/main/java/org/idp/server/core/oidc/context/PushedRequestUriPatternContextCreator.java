package org.idp.server.core.oidc.context;

import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.type.oauth.RequestUri;
import org.idp.server.core.oidc.OAuthRequestContext;
import org.idp.server.core.oidc.OAuthRequestPattern;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.exception.OAuthBadRequestException;
import org.idp.server.core.oidc.repository.AuthorizationRequestRepository;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;
import org.idp.server.core.oidc.request.OAuthRequestParameters;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/** PushedRequestUriPatternContextService */
public class PushedRequestUriPatternContextCreator implements OAuthRequestContextCreator {

  AuthorizationRequestRepository authorizationRequestRepository;

  public PushedRequestUriPatternContextCreator(
      AuthorizationRequestRepository authorizationRequestRepository) {
    this.authorizationRequestRepository = authorizationRequestRepository;
  }

  @Override
  public OAuthRequestContext create(
      Tenant tenant,
      OAuthRequestParameters parameters,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {

    OAuthRequestPattern pattern = OAuthRequestPattern.PUSHED_REQUEST_URI;
    RequestUri requestUri = parameters.requestUri();
    AuthorizationRequestIdentifier identifier =
        new AuthorizationRequestIdentifier(requestUri.extractId());
    AuthorizationRequest authorizationRequest =
        authorizationRequestRepository.find(tenant, identifier);

    if (!authorizationRequest.exists()) {
      throw new OAuthBadRequestException("invalid_request", "request uri does not exists", tenant);
    }

    return new OAuthRequestContext(
        tenant,
        pattern,
        parameters,
        new JoseContext(),
        authorizationRequest,
        authorizationServerConfiguration,
        clientConfiguration);
  }
}
