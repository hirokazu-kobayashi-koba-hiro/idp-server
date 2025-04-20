package org.idp.server.core.authentication;

import java.time.LocalDateTime;
import java.util.List;
import org.idp.server.core.basic.date.SystemDateTime;
import org.idp.server.core.ciba.handler.io.CibaIssueResponse;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.io.OAuthRequestResponse;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.AuthorizationFlow;
import org.idp.server.core.type.oauth.ExpiresIn;
import org.idp.server.core.type.oauth.RequestedClientId;

public class AuthenticationTransaction {
  AuthenticationTransactionIdentifier identifier;
  AuthenticationRequest request;
  AuthenticationInteractionResults interactionResults;

  public static AuthenticationTransaction createOnOAuthFlow(
      Tenant tenant, OAuthRequestResponse requestResponse) {
    AuthenticationTransactionIdentifier identifier =
        new AuthenticationTransactionIdentifier(requestResponse.authorizationRequestIdentifier());
    AuthenticationRequest authenticationRequest = toAuthenticationRequest(tenant, requestResponse);
    return new AuthenticationTransaction(identifier, authenticationRequest);
  }

  public static AuthenticationTransaction createOnCibaFlow(
      Tenant tenant, CibaIssueResponse cibaIssueResponse) {
    AuthenticationTransactionIdentifier identifier =
        new AuthenticationTransactionIdentifier(
            cibaIssueResponse.backchannelAuthenticationRequestIdentifier());
    AuthenticationRequest authenticationRequest =
        toAuthenticationRequest(tenant, cibaIssueResponse);
    return new AuthenticationTransaction(identifier, authenticationRequest);
  }

  private static AuthenticationRequest toAuthenticationRequest(
      Tenant tenant, OAuthRequestResponse requestResponse) {
    AuthorizationRequest authorizationRequest = requestResponse.authorizationRequest();
    AuthorizationFlow authorizationFlow = AuthorizationFlow.CIBA;
    TenantIdentifier tenantIdentifier = tenant.identifier();

    RequestedClientId requestedClientId = authorizationRequest.retrieveClientId();
    User user = User.notFound();
    List<String> availableAuthenticationTypes = requestResponse.availableAuthenticationTypes();
    List<String> requiredAnyOfAuthenticationTypes =
        requestResponse.requiredAnyOfAuthenticationTypes();
    LocalDateTime createdAt = SystemDateTime.now();
    LocalDateTime expiredAt =
        createdAt.plusSeconds(requestResponse.oauthAuthorizationRequestExpiresIn());
    return new AuthenticationRequest(
        authorizationFlow,
        tenantIdentifier,
        requestedClientId,
        user,
        availableAuthenticationTypes,
        requiredAnyOfAuthenticationTypes,
        createdAt,
        expiredAt);
  }

  private static AuthenticationRequest toAuthenticationRequest(
      Tenant tenant, CibaIssueResponse cibaIssueResponse) {
    BackchannelAuthenticationRequest backchannelAuthenticationRequest = cibaIssueResponse.request();
    ExpiresIn expiresIn = cibaIssueResponse.expiresIn();
    AuthorizationFlow authorizationFlow = AuthorizationFlow.CIBA;
    TenantIdentifier tenantIdentifier = tenant.identifier();

    RequestedClientId requestedClientId = backchannelAuthenticationRequest.requestedClientId();
    User user = cibaIssueResponse.user();
    List<String> availableAuthenticationTypes = cibaIssueResponse.availableAuthenticationTypes();
    List<String> requiredAnyOfAuthenticationTypes =
        cibaIssueResponse.requiredAnyOfAuthenticationTypes();
    LocalDateTime createdAt = SystemDateTime.now();
    LocalDateTime expiredAt = createdAt.plusSeconds(expiresIn.value());
    return new AuthenticationRequest(
        authorizationFlow,
        tenantIdentifier,
        requestedClientId,
        user,
        availableAuthenticationTypes,
        requiredAnyOfAuthenticationTypes,
        createdAt,
        expiredAt);
  }

  public AuthenticationTransaction() {}

  AuthenticationTransaction(
      AuthenticationTransactionIdentifier identifier, AuthenticationRequest request) {
    this(identifier, request, new AuthenticationInteractionResults());
  }

  public AuthenticationTransaction(
      AuthenticationTransactionIdentifier identifier,
      AuthenticationRequest request,
      AuthenticationInteractionResults interactionResults) {
    this.identifier = identifier;
    this.request = request;
    this.interactionResults = interactionResults;
  }

  public AuthenticationTransactionIdentifier identifier() {
    return identifier;
  }

  public AuthenticationRequest request() {
    return request;
  }

  public User user() {
    if (request.hasUser()) {
      return request.user();
    }
    return User.notFound();
  }

  public AuthenticationInteractionResults interactionResults() {
    return interactionResults;
  }
}
