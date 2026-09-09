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

package org.idp.server.core.openid.oauth.type;

import java.util.EnumSet;
import java.util.Set;
import org.idp.server.platform.exception.UnSupportedException;

public enum StandardAuthFlow {
  OAUTH("oauth"),
  CIBA("ciba"),
  FIDO_UAF_REGISTRATION("fido-uaf-registration"),
  FIDO_UAF_DEREGISTRATION("fido-uaf-deregistration"),
  FIDO2_REGISTRATION("fido2-registration"),
  FIDO2_DEREGISTRATION("fido2-deregistration"),
  MFA_SMS_REGISTRATION("mfa-sms-registration"),
  MFA_EMAIL_REGISTRATION("mfa-email-registration"),
  EMAIL_VERIFY("email-verify"),
  EMAIL_CHANGE("email-change");

  String value;

  StandardAuthFlow(String value) {
    this.value = value;
  }

  public static StandardAuthFlow of(String flow) {
    for (StandardAuthFlow standardAuthFlow : StandardAuthFlow.values()) {
      if (standardAuthFlow.value.equals(flow)) {
        return standardAuthFlow;
      }
    }
    throw new UnSupportedException(String.format("unsupported auth flow (%s)", flow));
  }

  public String value() {
    return value;
  }

  public AuthFlow toAuthFlow() {
    return new AuthFlow(this.value);
  }

  /**
   * Flows that may only be minted by their own dedicated, scope-gated endpoint.
   *
   * <p><b>Add a flow here whenever its endpoint enforces a scope or any other authorization beyond
   * "the token is valid".</b> The generic {@code POST /{tenant-id}/v1/me/mfa/{mfa-operation-type}}
   * minter takes the flow straight from the path and checks no scope, so anything omitted from this
   * set can be minted from there with a bare token — and every downstream guard is powerless,
   * because it inspects the persisted transaction, which is indistinguishable from one the proper
   * endpoint created (Issue #1416).
   *
   * <p>Deliberately a deny set, not an allow set: {@code flow} is a free-form string on {@link
   * org.idp.server.core.openid.authentication.policy.AuthenticationPolicyConfiguration} and nothing
   * validates it against this enum, so a tenant may have registered a policy under a name that is
   * not listed here. Allow-listing would break those.
   */
  private static final Set<StandardAuthFlow> DEDICATED_ENDPOINT_ONLY =
      EnumSet.of(EMAIL_VERIFY, EMAIL_CHANGE);

  /**
   * Whether {@code authFlow} is one of the {@link #DEDICATED_ENDPOINT_ONLY} flows, and therefore
   * must be rejected by the generic MFA minter. Unknown flows return {@code false} — see the
   * field's javadoc for why that is the intended default.
   */
  public static boolean isDedicatedEndpointOnly(AuthFlow authFlow) {
    return DEDICATED_ENDPOINT_ONLY.stream()
        .anyMatch(standardAuthFlow -> standardAuthFlow.toAuthFlow().equals(authFlow));
  }
}
