package org.idp.server.core;

import java.util.Map;
import org.idp.server.core.api.TokenApi;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.handler.token.io.TokenRequest;
import org.idp.server.core.handler.token.io.TokenRequestResponse;
import org.idp.server.core.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.core.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.core.handler.tokenrevocation.io.TokenRevocationRequest;
import org.idp.server.core.handler.tokenrevocation.io.TokenRevocationResponse;
import org.idp.server.core.oauth.identity.PasswordVerificationDelegation;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.protocol.TokenIntrospectionProtocol;
import org.idp.server.core.protocol.TokenProtocol;
import org.idp.server.core.protocol.TokenRevocationProtocol;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.tenant.TenantRepository;
import org.idp.server.core.token.PasswordCredentialsGrantDelegate;
import org.idp.server.core.type.oauth.Password;
import org.idp.server.core.type.oauth.Username;

@Transactional
public class TokenEntryService implements TokenApi, PasswordCredentialsGrantDelegate {

  TokenProtocol tokenProtocol;
  TokenIntrospectionProtocol tokenIntrospectionProtocol;
  TokenRevocationProtocol tokenRevocationProtocol;
  TenantRepository tenantRepository;
  UserRepository userRepository;
  PasswordVerificationDelegation passwordVerificationDelegation;

  public TokenEntryService(
      TokenProtocol tokenProtocol,
      TokenIntrospectionProtocol tokenIntrospectionProtocol,
      TokenRevocationProtocol tokenRevocationProtocol,
      UserRepository userRepository,
      TenantRepository tenantRepository,
      PasswordVerificationDelegation passwordVerificationDelegation) {
    this.tokenProtocol = tokenProtocol;
    tokenProtocol.setPasswordCredentialsGrantDelegate(this);
    this.tokenIntrospectionProtocol = tokenIntrospectionProtocol;
    this.tokenRevocationProtocol = tokenRevocationProtocol;
    this.tenantRepository = tenantRepository;
    this.userRepository = userRepository;
    this.passwordVerificationDelegation = passwordVerificationDelegation;
  }

  public TokenRequestResponse request(
      TenantIdentifier tenantId,
      Map<String, String[]> params,
      String authorizationHeader,
      String clientCert) {

    Tenant tenant = tenantRepository.get(tenantId);
    TokenRequest tokenRequest = new TokenRequest(tenant, authorizationHeader, params);
    tokenRequest.setClientCert(clientCert);

    return tokenProtocol.request(tokenRequest);
  }

  public TokenIntrospectionResponse inspect(
      TenantIdentifier tenantIdentifier, Map<String, String[]> params) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    TokenIntrospectionRequest tokenIntrospectionRequest =
        new TokenIntrospectionRequest(tenant, params);

    return tokenIntrospectionProtocol.inspect(tokenIntrospectionRequest);
  }

  public TokenRevocationResponse revoke(
      TenantIdentifier tenantId,
      Map<String, String[]> request,
      String authorizationHeader,
      String clientCert) {

    Tenant tenant = tenantRepository.get(tenantId);
    TokenRevocationRequest revocationRequest =
        new TokenRevocationRequest(tenant, authorizationHeader, request);
    revocationRequest.setClientCert(clientCert);

    return tokenRevocationProtocol.revoke(revocationRequest);
  }

  @Override
  public User findAndAuthenticate(Tenant tenant, Username username, Password password) {
    User user = userRepository.findBy(tenant, username.value(), "idp-server");
    if (!user.exists()) {
      return User.notFound();
    }

    if (!passwordVerificationDelegation.verify(password.value(), user.hashedPassword())) {
      return User.notFound();
    }

    return user;
  }
}
