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

import org.idp.server.core.openid.authentication.config.AuthenticationVerificationConfig;
import org.idp.server.platform.json.path.JsonPathWrapper;

/** Verifies the result of an authentication interaction after its execution. */
public interface AuthenticationPostHookVerifier {

  String type();

  /**
   * Verifies the context against the settings of the verification.
   *
   * @param verificationConfig the verification settings
   * @param context the context, which has the roots {@code $.request_body}, {@code
   *     $.request_attributes}, {@code $.interaction} and the execution result such as {@code
   *     $.execution_http_request}
   */
  AuthenticationPostHookVerificationResult verify(
      AuthenticationVerificationConfig verificationConfig, JsonPathWrapper context);
}
