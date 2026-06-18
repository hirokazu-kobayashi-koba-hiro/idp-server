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
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.id_token.VerifiedClaimsObject;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.userinfo.plugin.UserinfoCustomIndividualClaimsCreator;
import org.idp.server.platform.json.JsonNodeWrapper;

/**
 * Adds the OIDC4IDA {@code verified_claims} element to the UserInfo response when it was requested
 * via the {@code claims} parameter's {@code userinfo.verified_claims} member (OIDC4IDA §5.3 / §5.7,
 * eKYC conformance module #9).
 *
 * <p>UserInfo runs from an access token with no live request, so the requested structure is read
 * from the grant — where it was persisted as the consent record at authorization time ({@link
 * org.idp.server.core.openid.grant_management.grant.GrantUserinfoClaims}). The assembly
 * (value/values constraints, §5.7 omission, required trust_framework) is shared with the ID Token
 * path via {@link VerifiedClaimsAssembler}. The scope-driven counterpart ({@code
 * verified_claims:*}) is {@link UserinfoSelectiveVerifiedClaimsCreator}; since both emit the same
 * top-level {@code verified_claims} key, this claims-parameter path takes precedence and the
 * scope-driven creator stands down when a claims-parameter request is present. (#1628)
 */
public class UserinfoVerifiedClaimsCreator implements UserinfoCustomIndividualClaimsCreator {

  @Override
  public boolean shouldCreate(
      User user,
      AuthorizationGrant authorizationGrant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {
    return authorizationGrant.userinfoClaims().hasVerifiedClaims() && user.hasVerifiedClaims();
  }

  @Override
  public Map<String, Object> create(
      User user,
      AuthorizationGrant authorizationGrant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {
    VerifiedClaimsObject requested = authorizationGrant.userinfoClaims().verifiedClaims();
    JsonNodeWrapper userVerifiedClaims = user.verifiedClaimsNodeWrapper();
    return VerifiedClaimsAssembler.assemble(requested, userVerifiedClaims);
  }
}
