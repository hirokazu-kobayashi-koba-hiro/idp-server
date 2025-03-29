package org.idp.server.core.oauth.grant;

import java.util.HashSet;
import java.util.Set;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.client.Client;
import org.idp.server.core.oauth.client.ClientIdentifier;
import org.idp.server.core.oauth.client.ClientName;
import org.idp.server.core.oauth.grant.consent.ConsentClaims;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.rar.AuthorizationDetails;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.extension.CustomProperties;
import org.idp.server.core.type.oauth.RequestedClientId;
import org.idp.server.core.type.oauth.Scopes;
import org.idp.server.core.type.oauth.Subject;

public class AuthorizationGrant {

  TenantIdentifier tenantIdentifier;
  User user;
  Authentication authentication;
  RequestedClientId requestedClientId;
  Client client;
  Scopes scopes;
  GrantIdTokenClaims idTokenClaims;
  GrantUserinfoClaims userinfoClaims;
  CustomProperties customProperties;
  AuthorizationDetails authorizationDetails;
  ConsentClaims consentClaims;

  public AuthorizationGrant() {}

  public AuthorizationGrant(
      TenantIdentifier tenantIdentifier,
      User user,
      Authentication authentication,
      RequestedClientId requestedClientId,
      Client client,
      Scopes scopes,
      GrantIdTokenClaims idTokenClaims,
      GrantUserinfoClaims userinfoClaims,
      CustomProperties customProperties,
      AuthorizationDetails authorizationDetails,
      ConsentClaims consentClaims) {
    this.tenantIdentifier = tenantIdentifier;
    this.user = user;
    this.authentication = authentication;
    this.requestedClientId = requestedClientId;
    this.client = client;
    this.scopes = scopes;
    this.idTokenClaims = idTokenClaims;
    this.userinfoClaims = userinfoClaims;
    this.customProperties = customProperties;
    this.authorizationDetails = authorizationDetails;
    this.consentClaims = consentClaims;
  }

  public TenantIdentifier tenantIdentifier() {
    return tenantIdentifier;
  }

  public User user() {
    return user;
  }

  public Authentication authentication() {
    return authentication;
  }

  public String subjectValue() {
    return user.sub();
  }

  public Subject subject() {
    return new Subject(user.sub());
  }

  public RequestedClientId requestedClientId() {
    return requestedClientId;
  }

  public Client client() {
    return client;
  }

  public ClientIdentifier clientIdentifier() {
    return client.identifier();
  }

  public ClientName clientName() {
    return client.name();
  }

  public String clientIdentifierValue() {
    return client.identifier().value();
  }

  public Scopes scopes() {
    return scopes;
  }

  public GrantIdTokenClaims idTokenClaims() {
    return idTokenClaims;
  }

  public GrantUserinfoClaims userinfoClaims() {
    return userinfoClaims;
  }

  public String scopesValue() {
    return scopes.toStringValues();
  }

  public CustomProperties customProperties() {
    return customProperties;
  }

  public boolean hasCustomProperties() {
    return customProperties.exists();
  }

  public boolean isGranted(ClientIdentifier clientIdentifier) {
    return this.clientIdentifier().equals(clientIdentifier);
  }

  public boolean hasUser() {
    return user.exists();
  }

  public boolean hasOpenidScope() {
    return scopes.contains("openid");
  }

  public AuthorizationDetails authorizationDetails() {
    return authorizationDetails;
  }

  public boolean hasAuthorizationDetails() {
    return authorizationDetails.exists();
  }

  public boolean hasIdTokenClaims() {
    return idTokenClaims.exists();
  }

  public boolean hasUserinfoClaim() {
    return userinfoClaims.exists();
  }

  public ConsentClaims consentClaims() {
    return consentClaims;
  }

  public boolean hasConsentClaims() {
    return consentClaims.exists();
  }

  // TODO
  public AuthorizationGrant merge(AuthorizationGrant newAuthorizationGrant) {
    User newUser = newAuthorizationGrant.user();
    Authentication newAuthentication = newAuthorizationGrant.authentication();
    RequestedClientId newRequestClientId = newAuthorizationGrant.requestedClientId();
    Client newClient = newAuthorizationGrant.client();

    Set<String> newScopeValues = new HashSet<>(this.scopes.toStringSet());
    newAuthorizationGrant.scopes().forEach(newScopeValues::add);
    Scopes newScopes = new Scopes(newScopeValues);

    Set<String> newIdTokenClaims = new HashSet<>(idTokenClaims.toStringSet());
    newAuthorizationGrant.idTokenClaims().forEach(newIdTokenClaims::add);
    GrantIdTokenClaims newGrantIdToken = new GrantIdTokenClaims(newIdTokenClaims);

    Set<String> newClaims = new HashSet<>(userinfoClaims.toStringSet());
    newAuthorizationGrant.userinfoClaims().forEach(newClaims::add);
    GrantUserinfoClaims newGrantUserinfo = new GrantUserinfoClaims(newClaims);

    CustomProperties newCustomProperties = newAuthorizationGrant.customProperties();
    AuthorizationDetails newAuthorizationDetails = newAuthorizationGrant.authorizationDetails();

    ConsentClaims newConsentClaims = consentClaims.merge(newAuthorizationGrant.consentClaims());

    return new AuthorizationGrant(
        tenantIdentifier,
        newUser,
        newAuthentication,
        newRequestClientId,
        newClient,
        newScopes,
        newGrantIdToken,
        newGrantUserinfo,
        newCustomProperties,
        newAuthorizationDetails,
        newConsentClaims);
  }
}
