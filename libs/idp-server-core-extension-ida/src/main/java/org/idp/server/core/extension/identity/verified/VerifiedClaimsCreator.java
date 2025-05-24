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

package org.idp.server.core.extension.identity.verified;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.core.oidc.authentication.Authentication;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.grant.AuthorizationGrant;
import org.idp.server.core.oidc.grant.GrantIdTokenClaims;
import org.idp.server.core.oidc.id_token.*;
import org.idp.server.core.oidc.id_token.plugin.CustomIndividualClaimsCreator;
import org.idp.server.core.oidc.identity.User;

public class VerifiedClaimsCreator implements CustomIndividualClaimsCreator {

  @Override
  public boolean shouldCreate(
      User user,
      Authentication authentication,
      AuthorizationGrant authorizationGrant,
      IdTokenCustomClaims customClaims,
      RequestedClaimsPayload requestedClaimsPayload,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {

    GrantIdTokenClaims idTokenClaims = authorizationGrant.idTokenClaims();
    return idTokenClaims.hasVerifiedClaims() && user.hasVerifiedClaims();
  }

  @Override
  public Map<String, Object> create(
      User user,
      Authentication authentication,
      AuthorizationGrant authorizationGrant,
      IdTokenCustomClaims customClaims,
      RequestedClaimsPayload requestedClaimsPayload,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {

    RequestedIdTokenClaims requestedIdTokenClaims = requestedClaimsPayload.idToken();

    HashMap<String, Object> claims = new HashMap<>();

    VerifiedClaimsObject verifiedClaimsObject = requestedIdTokenClaims.verifiedClaims();
    JsonNodeWrapper userVerifiedClaims = user.verifiedClaimsNodeWrapper();
    Map<String, Object> verified = new HashMap<>();

    JsonNodeWrapper verificationNodeWrapper = verifiedClaimsObject.verificationNodeWrapper();
    Map<String, Object> verification = new HashMap<>();
    JsonNodeWrapper verificationClaim = userVerifiedClaims.getValueAsJsonNode("verification");
    if (verificationNodeWrapper.contains("trust_framework")
        && verificationClaim.contains("trust_framework")) {
      verification.put(
          "trust_framework", verificationClaim.getValueOrEmptyAsString("trust_framework"));
    }
    if (verificationNodeWrapper.contains("evidence") && verificationClaim.contains("evidence")) {
      verification.put("evidence", verificationClaim.getValueAsJsonNodeListAsMap("evidence"));
    }

    JsonNodeWrapper claimsNodeWrapper = verifiedClaimsObject.claimsNodeWrapper();
    JsonNodeWrapper userClaims = userVerifiedClaims.getValueAsJsonNode("claims");
    Map<String, Object> verifiedClaims = new HashMap<>();
    if (claimsNodeWrapper.contains("name") && userClaims.contains("name")) {
      verifiedClaims.put("name", userClaims.getValueOrEmptyAsString("name"));
    }
    if (claimsNodeWrapper.contains("given_name") && userClaims.contains("given_name")) {
      verifiedClaims.put("given_name", userClaims.getValueOrEmptyAsString("given_name"));
    }
    if (claimsNodeWrapper.contains("family_name") && userClaims.contains("family_name")) {
      verifiedClaims.put("family_name", userClaims.getValueOrEmptyAsString("family_name"));
    }
    if (claimsNodeWrapper.contains("middle_name") && userClaims.contains("middle_name")) {
      verifiedClaims.put("middle_name", userClaims.getValueOrEmptyAsString("middle_name"));
    }
    if (claimsNodeWrapper.contains("gender") && userClaims.contains("gender")) {
      verifiedClaims.put("gender", userClaims.getValueOrEmptyAsString("gender"));
    }
    if (claimsNodeWrapper.contains("birthdate") && userClaims.contains("birthdate")) {
      verifiedClaims.put("birthdate", userClaims.getValueOrEmptyAsString("birthdate"));
    }
    if (claimsNodeWrapper.contains("locale") && userClaims.contains("locale")) {
      verifiedClaims.put("locale", userClaims.getValueOrEmptyAsString("locale"));
    }
    if (claimsNodeWrapper.contains("address") && userClaims.contains("address")) {
      verifiedClaims.put("address", userClaims.getValueAsJsonNode("address").toMap());
    }
    if (claimsNodeWrapper.contains("phone_number") && userClaims.contains("phone_number")) {
      verifiedClaims.put("phone_number", userClaims.getValueOrEmptyAsString("phone_number"));
    }
    if (claimsNodeWrapper.contains("phone_number_verified")
        && userClaims.contains("phone_number_verified")) {
      verifiedClaims.put(
          "phone_number_verified", userClaims.getValueAsBoolean("phone_number_verified"));
    }
    if (claimsNodeWrapper.contains("email") && userClaims.contains("email")) {
      verifiedClaims.put("email", userClaims.getValueOrEmptyAsString("email"));
    }
    if (claimsNodeWrapper.contains("email_verified") && userClaims.contains("email_verified")) {
      verifiedClaims.put("email_verified", userVerifiedClaims.getValueAsBoolean("email_verified"));
    }
    verified.put("verification", verification);
    verified.put("claims", verifiedClaims);
    claims.put("verified_claims", verified);

    return claims;
  }
}
