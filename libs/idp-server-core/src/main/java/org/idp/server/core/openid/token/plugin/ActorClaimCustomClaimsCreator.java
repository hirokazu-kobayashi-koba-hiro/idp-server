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

package org.idp.server.core.openid.token.plugin;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;

/**
 * ActorClaimCustomClaimsCreator
 *
 * <p>Creates the {@code act} (actor) claim for Delegation in Token Exchange (RFC 8693 Section 4.1).
 *
 * <p>RFC 8693 Section 4.1:
 *
 * <blockquote>
 *
 * The "act" (actor) claim provides a means of expressing that delegation has occurred and
 * identifying the acting party to whom authority has been delegated. The "act" claim value is a
 * JSON object, and members in the JSON object are claims that identify the actor.
 *
 * </blockquote>
 *
 * <p>RFC 8693 Section 4.4:
 *
 * <blockquote>
 *
 * A chain of delegation can be expressed by nesting one "act" claim within another.
 *
 * </blockquote>
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693#section-4.1">RFC 8693 Section 4.1</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693#section-4.4">RFC 8693 Section 4.4</a>
 */
public class ActorClaimCustomClaimsCreator implements AccessTokenCustomClaimsCreator {

  @Override
  public boolean shouldCreate(
      AuthorizationGrant authorizationGrant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration,
      ClientCredentials clientCredentials) {
    return authorizationGrant.hasCustomProperties()
        && authorizationGrant.customProperties().contains("act_sub");
  }

  @Override
  public Map<String, Object> create(
      AuthorizationGrant authorizationGrant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration,
      ClientCredentials clientCredentials) {

    String actorSub = (String) authorizationGrant.customProperties().getValue("act_sub");

    Map<String, Object> actClaim = new HashMap<>();
    actClaim.put("sub", actorSub);

    // Support delegation chain nesting (RFC 8693 Section 4.4)
    Object actChain = authorizationGrant.customProperties().getValue("act_chain");
    if (actChain != null) {
      actClaim.put("act", actChain);
    }

    return Map.of("act", actClaim);
  }
}
