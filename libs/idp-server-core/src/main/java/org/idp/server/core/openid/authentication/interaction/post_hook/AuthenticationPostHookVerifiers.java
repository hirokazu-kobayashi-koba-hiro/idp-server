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
package org.idp.server.core.openid.authentication.interaction.post_hook;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.authentication.config.AuthenticationPostHookConfig;
import org.idp.server.core.openid.authentication.config.AuthenticationVerificationConfig;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.log.LoggerWrapper;

/**
 * Runs the post_hook verifications of an authentication interaction.
 *
 * <p>The verifications run in order and stop at the first failure. An unknown {@code type} fails,
 * so a typo in the configuration does not silently turn the verification off.
 */
public class AuthenticationPostHookVerifiers {

  Map<String, AuthenticationPostHookVerifier> verifiers = new HashMap<>();
  LoggerWrapper log = LoggerWrapper.getLogger(AuthenticationPostHookVerifiers.class);

  public AuthenticationPostHookVerifiers() {
    MatchVerifier matchVerifier = new MatchVerifier();
    verifiers.put(matchVerifier.type(), matchVerifier);
  }

  /**
   * Runs the verifications against the context.
   *
   * @param postHookConfig the post_hook settings of the interaction
   * @param context the roots that the JSONPaths refer to
   */
  public AuthenticationPostHookVerificationResult verifyAll(
      AuthenticationPostHookConfig postHookConfig, Map<String, Object> context) {

    if (!postHookConfig.hasVerifications()) {
      return AuthenticationPostHookVerificationResult.success();
    }

    JsonPathWrapper jsonPath = new JsonPathWrapper(JsonNodeWrapper.fromMap(context).toJson());

    for (AuthenticationVerificationConfig verificationConfig : postHookConfig.verifications()) {

      if (verificationConfig == null) {
        log.error("post_hook verification misconfigured: null element");
        return AuthenticationPostHookVerificationResult.failure(
            List.of("post_hook verification is misconfigured. null element."));
      }

      AuthenticationPostHookVerifier verifier = verifiers.get(verificationConfig.type());
      if (verifier == null) {
        log.error("post_hook verifier is undefined. type: {}", verificationConfig.type());
        return AuthenticationPostHookVerificationResult.failure(
            List.of(
                String.format(
                    "post_hook verifier is undefined. type: %s", verificationConfig.type())));
      }

      AuthenticationPostHookVerificationResult result =
          verifier.verify(verificationConfig, jsonPath);
      if (result.isFailure()) {
        return result;
      }
    }

    return AuthenticationPostHookVerificationResult.success();
  }
}
