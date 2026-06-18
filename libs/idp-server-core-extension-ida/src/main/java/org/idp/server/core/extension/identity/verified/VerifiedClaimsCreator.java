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

import java.util.Map;
import org.idp.server.core.openid.authentication.Authentication;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.grant_management.grant.GrantIdTokenClaims;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.id_token.IdTokenCustomClaims;
import org.idp.server.core.openid.identity.id_token.RequestedClaimsPayload;
import org.idp.server.core.openid.identity.id_token.VerifiedClaimsObject;
import org.idp.server.core.openid.identity.id_token.plugin.CustomIndividualClaimsCreator;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.platform.json.JsonNodeWrapper;

/**
 * Builds the ID Token {@code verified_claims} element from the {@code id_token.verified_claims}
 * request that was persisted with the grant (the consent record), falling back to the live {@code
 * claims} parameter for legacy grants. Reading from the grant lets the ID Token be built without
 * the original request. The OIDC4IDA §5.1 / §5.7 assembly (including the value/values constraint
 * and omission rules) lives in {@link VerifiedClaimsAssembler}, shared with the UserInfo path; this
 * creator just supplies the requested structure and the user's stored verified claims. (#1628)
 *
 * @see <a href="https://openid.net/specs/openid-connect-4-identity-assurance-1_0.html">OpenID
 *     Connect for Identity Assurance 1.0</a>
 */
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

    // Prefer the grant's persisted request (the consent record) so the ID Token can be built from
    // the grant alone — without the original claims parameter, which is gone by the time a token is
    // re-issued. Fall back to the live request for legacy grants that recorded only the bare name
    // token (pre-sentinel). (#1628 follow-up)
    VerifiedClaimsObject requested = authorizationGrant.idTokenClaims().verifiedClaims();
    if (requested == null) {
      requested = requestedClaimsPayload.idToken().verifiedClaims();
    }
    JsonNodeWrapper userVerifiedClaims = user.verifiedClaimsNodeWrapper();

    return VerifiedClaimsAssembler.assemble(requested, userVerifiedClaims);
  }
}
