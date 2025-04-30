package org.idp.server.core;

import java.util.Map;
import org.idp.server.core.basic.datasource.Transaction;
import org.idp.server.core.identity.UserRepository;
import org.idp.server.core.security.event.TokenEventPublisher;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.tenant.TenantRepository;
import org.idp.server.core.token.TokenApi;
import org.idp.server.core.token.TokenProtocol;
import org.idp.server.core.token.TokenProtocols;
import org.idp.server.core.token.handler.token.io.TokenRequest;
import org.idp.server.core.token.handler.token.io.TokenRequestResponse;
import org.idp.server.core.token.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.core.token.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.core.token.handler.tokenrevocation.io.TokenRevocationRequest;
import org.idp.server.core.token.handler.tokenrevocation.io.TokenRevocationResponse;
import org.idp.server.core.type.security.RequestAttributes;

@Transaction
public class TokenEntryService implements TokenApi {

  TokenProtocols tokenProtocols;
  TenantRepository tenantRepository;
  UserRepository userRepository;
  TokenEventPublisher eventPublisher;

  public TokenEntryService(
      TokenProtocols tokenProtocols,
      UserRepository userRepository,
      TenantRepository tenantRepository,
      TokenEventPublisher eventPublisher) {
    this.tokenProtocols = tokenProtocols;
    this.tenantRepository = tenantRepository;
    this.userRepository = userRepository;
    this.eventPublisher = eventPublisher;
  }

  public TokenRequestResponse request(
      TenantIdentifier tenantIdentifier,
      Map<String, String[]> params,
      String authorizationHeader,
      String clientCert,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    TokenRequest tokenRequest = new TokenRequest(tenant, authorizationHeader, params);
    tokenRequest.setClientCert(clientCert);

    TokenProtocol tokenProtocol = tokenProtocols.get(tenant.authorizationProtocolProvider());

    TokenRequestResponse requestResponse = tokenProtocol.request(tokenRequest);

    if (requestResponse.isOK()) {
      eventPublisher.publish(
          tenant,
          requestResponse.oAuthToken(),
          requestResponse.securityEventType(tokenRequest),
          requestAttributes);
    }

    return requestResponse;
  }

  public TokenIntrospectionResponse inspect(
      TenantIdentifier tenantIdentifier,
      Map<String, String[]> params,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    TokenIntrospectionRequest tokenIntrospectionRequest =
        new TokenIntrospectionRequest(tenant, params);

    TokenProtocol tokenProtocol = tokenProtocols.get(tenant.authorizationProtocolProvider());

    TokenIntrospectionResponse result = tokenProtocol.inspect(tokenIntrospectionRequest);

    if (result.hasOAuthToken()) {
      eventPublisher.publish(
          tenant, result.oAuthToken(), result.securityEventType(), requestAttributes);
    }

    return result;
  }

  public TokenRevocationResponse revoke(
      TenantIdentifier tenantIdentifier,
      Map<String, String[]> request,
      String authorizationHeader,
      String clientCert,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    TokenRevocationRequest revocationRequest =
        new TokenRevocationRequest(tenant, authorizationHeader, request);
    revocationRequest.setClientCert(clientCert);

    TokenProtocol tokenProtocol = tokenProtocols.get(tenant.authorizationProtocolProvider());

    TokenRevocationResponse result = tokenProtocol.revoke(revocationRequest);

    if (result.hasOAuthToken()) {
      eventPublisher.publish(
          tenant, result.oAuthToken(), result.securityEventType(), requestAttributes);
    }

    return result;
  }
}
