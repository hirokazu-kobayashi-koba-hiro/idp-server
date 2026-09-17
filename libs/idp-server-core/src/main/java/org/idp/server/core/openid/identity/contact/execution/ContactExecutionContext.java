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

package org.idp.server.core.openid.identity.contact.execution;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallenge;
import org.idp.server.platform.type.RequestAttributes;

/**
 * The mapping source a contact HTTP execution sees (Issue #1416).
 *
 * <p>Shared by the single and chained executors so the two cannot drift: on the login path the
 * {@code $.user.*} projection was wired into one executor and not the other, and the same mapping
 * rule silently resolved to null depending on which one the interaction happened to use (Issue
 * #1767). Building it in one place is the cheapest way to not repeat that.
 *
 * <p>Keys are the ones the login executors publish, so tenant mapping rules transfer unchanged.
 */
class ContactExecutionContext {

  static Map<String, Object> create(
      ContactVerificationChallenge challenge,
      ContactExecutionRequest request,
      RequestAttributes requestAttributes,
      AuthenticationExecutionConfig configuration) {

    Map<String, Object> param = new HashMap<>();
    param.put("request_body", request.toMap());
    // Issue #1773: the attribute map, not the wrapper object — serializing the object itself put
    // it under {"json_node_wrapper":{"json_node":{...}}} and $.request_attributes.ip_address
    // resolved to null while looking configured.
    param.put("request_attributes", requestAttributes.toMap());

    if (request.hasUser()) {
      param.put("user", request.user());
    }

    // The configured previous_interaction key is accepted and ignored: a challenge row holds one
    // exchange, so there is nothing to select between.
    if (configuration.hasPreviousInteraction() && challenge.exists()) {
      param.put("interaction", challenge.externalReference());
    }

    return param;
  }
}
