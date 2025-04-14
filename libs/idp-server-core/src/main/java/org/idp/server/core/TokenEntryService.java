package org.idp.server.core;

import java.util.Map;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.oauth.identity.UserRepository;
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

@Transactional
public class TokenEntryService implements TokenApi {

  TokenProtocols tokenProtocols;
  TenantRepository tenantRepository;
  UserRepository userRepository;

  public TokenEntryService(
      TokenProtocols tokenProtocols,
      UserRepository userRepository,
      TenantRepository tenantRepository) {
    this.tokenProtocols = tokenProtocols;
    this.tenantRepository = tenantRepository;
    this.userRepository = userRepository;
  }

  public TokenRequestResponse request(
      TenantIdentifier tenantIdentifier,
      Map<String, String[]> params,
      String authorizationHeader,
      String clientCert) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    TokenRequest tokenRequest = new TokenRequest(tenant, authorizationHeader, params);
    tokenRequest.setClientCert(clientCert);

    TokenProtocol tokenProtocol = tokenProtocols.get(tenant.authorizationProtocolProvider());

    return tokenProtocol.request(tokenRequest);
  }

  public TokenIntrospectionResponse inspect(
      TenantIdentifier tenantIdentifier, Map<String, String[]> params) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    TokenIntrospectionRequest tokenIntrospectionRequest =
        new TokenIntrospectionRequest(tenant, params);

    TokenProtocol tokenProtocol = tokenProtocols.get(tenant.authorizationProtocolProvider());

    return tokenProtocol.inspect(tokenIntrospectionRequest);
  }

  public TokenRevocationResponse revoke(
      TenantIdentifier tenantIdentifier,
      Map<String, String[]> request,
      String authorizationHeader,
      String clientCert) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    TokenRevocationRequest revocationRequest =
        new TokenRevocationRequest(tenant, authorizationHeader, request);
    revocationRequest.setClientCert(clientCert);

    TokenProtocol tokenProtocol = tokenProtocols.get(tenant.authorizationProtocolProvider());

    return tokenProtocol.revoke(revocationRequest);
  }
}
