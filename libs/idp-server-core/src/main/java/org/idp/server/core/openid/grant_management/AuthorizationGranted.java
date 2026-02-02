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

package org.idp.server.core.openid.grant_management;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.grant_management.grant.GrantIdTokenClaims;
import org.idp.server.core.openid.grant_management.grant.GrantUserinfoClaims;
import org.idp.server.core.openid.grant_management.grant.consent.ConsentClaims;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;

public class AuthorizationGranted {
  AuthorizationGrantedIdentifier identifier;
  AuthorizationGrant authorizationGrant;
  LocalDateTime createdAt;
  LocalDateTime updatedAt;

  public AuthorizationGranted() {}

  public AuthorizationGranted(
      AuthorizationGrantedIdentifier identifier, AuthorizationGrant authorizationGrant) {
    this.identifier = identifier;
    this.authorizationGrant = authorizationGrant;
  }

  public AuthorizationGranted(
      AuthorizationGrantedIdentifier identifier,
      AuthorizationGrant authorizationGrant,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {
    this.identifier = identifier;
    this.authorizationGrant = authorizationGrant;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public AuthorizationGrantedIdentifier identifier() {
    return identifier;
  }

  public AuthorizationGrant authorizationGrant() {
    return authorizationGrant;
  }

  public AuthorizationGranted replace(AuthorizationGrant authorizationGrant) {

    return new AuthorizationGranted(identifier, authorizationGrant);
  }

  public AuthorizationGranted merge(AuthorizationGrant newAuthorizationGrant) {

    AuthorizationGrant merged = authorizationGrant.merge(newAuthorizationGrant);
    return new AuthorizationGranted(identifier, merged);
  }

  public boolean exists() {
    return identifier != null && identifier.exists();
  }

  public boolean isGrantedScopes(Scopes requestedScopes) {
    return authorizationGrant.isGrantedScopes(requestedScopes);
  }

  public Scopes unauthorizedScopes(Scopes requestedScopes) {
    return authorizationGrant.unauthorizedScopes(requestedScopes);
  }

  public boolean isGrantedClaims(GrantIdTokenClaims requestedIdTokenClaims) {
    return authorizationGrant.isGrantedIdTokenClaims(requestedIdTokenClaims);
  }

  public boolean isGrantedClaims(GrantUserinfoClaims requestedUserinfoClaims) {
    return authorizationGrant.isGrantedUserinfoClaims(requestedUserinfoClaims);
  }

  public GrantIdTokenClaims unauthorizedIdTokenClaims(GrantIdTokenClaims grantIdTokenClaims) {
    return authorizationGrant.unauthorizedIdTokenClaims(grantIdTokenClaims);
  }

  public GrantUserinfoClaims unauthorizedIdTokenClaims(GrantUserinfoClaims grantUserinfoClaims) {
    return authorizationGrant.unauthorizedUserinfoClaims(grantUserinfoClaims);
  }

  public boolean isConsentedClaims(ConsentClaims requestedConsentClaims) {
    return authorizationGrant.isConsentedClaims(requestedConsentClaims);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("id", identifier.value());

    if (authorizationGrant != null) {
      Map<String, Object> userMap = new HashMap<>();
      if (authorizationGrant.user() != null && authorizationGrant.user().exists()) {
        userMap.put("sub", authorizationGrant.user().sub());
        userMap.put("name", authorizationGrant.user().name());
        userMap.put("email", authorizationGrant.user().email());
      }
      map.put("user", userMap);

      Map<String, Object> clientMap = new HashMap<>();
      if (authorizationGrant.clientAttributes() != null) {
        clientMap.put("client_id", authorizationGrant.clientIdentifierValue());
        if (authorizationGrant.clientName() != null) {
          clientMap.put("client_name", authorizationGrant.clientName().value());
        }
      }
      map.put("client", clientMap);

      if (authorizationGrant.scopes() != null) {
        map.put("scopes", authorizationGrant.scopes().toStringList());
      }

      if (authorizationGrant.consentClaims() != null
          && authorizationGrant.consentClaims().exists()) {
        map.put("consent_claims", authorizationGrant.consentClaims().toMap());
      }
    }

    if (createdAt != null) {
      map.put("created_at", createdAt.toString());
    }
    if (updatedAt != null) {
      map.put("updated_at", updatedAt.toString());
    }

    return map;
  }
}
