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

package org.idp.server.core.openid.oauth.verifier.extension;

import static org.junit.jupiter.api.Assertions.*;

import org.idp.server.core.openid.oauth.OAuthRequestContext;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.exception.OAuthRedirectableBadRequestException;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RequirePushedAuthorizationRequestVerifier} (M-6, RFC 9126 §5). The verifier
 * only consults three booleans on the context, so a lightweight subclass test double is enough (no
 * mocking framework is available in this module).
 */
class RequirePushedAuthorizationRequestVerifierTest {

  private final RequirePushedAuthorizationRequestVerifier verifier =
      new RequirePushedAuthorizationRequestVerifier();

  /** Test double: overrides only the three signals the verifier reads. */
  private static OAuthRequestContext context(
      boolean requirePar, boolean atPushedEndpoint, boolean pushedRequest) {
    AuthorizationServerConfiguration serverConfiguration =
        new AuthorizationServerConfiguration() {
          @Override
          public boolean requirePushedAuthorizationRequests() {
            return requirePar;
          }
        };
    return new OAuthRequestContext() {
      @Override
      public AuthorizationServerConfiguration serverConfiguration() {
        return serverConfiguration;
      }

      @Override
      public boolean isAtPushedEndpoint() {
        return atPushedEndpoint;
      }

      @Override
      public boolean isPushedRequest() {
        return pushedRequest;
      }
    };
  }

  @Test
  void shouldVerifyWhenFlagEnabledAtAuthorizationEndpoint() {
    // require=true, not the PAR endpoint -> the enforcement gate is active.
    assertTrue(verifier.shouldVerify(context(true, false, false)));
  }

  @Test
  void shouldNotVerifyAtPushedEndpoint() {
    // The PAR endpoint itself is where the request is pushed; enforcing there would be circular.
    assertFalse(verifier.shouldVerify(context(true, true, false)));
  }

  @Test
  void shouldNotVerifyWhenFlagDisabled() {
    // Flag off -> no enforcement regardless of how the request arrives (default behavior
    // preserved).
    assertFalse(verifier.shouldVerify(context(false, false, false)));
  }

  @Test
  void rejectsDirectAuthorizationRequest() {
    // Flag on and the request did not originate from PAR -> reject with invalid_request.
    OAuthRedirectableBadRequestException ex =
        assertThrows(
            OAuthRedirectableBadRequestException.class,
            () -> verifier.verify(context(true, false, false)));
    assertEquals("invalid_request", ex.error().value());
  }

  @Test
  void allowsPushedRequest() {
    // Flag on but the request came via a PAR-issued request_uri -> allowed.
    assertDoesNotThrow(() -> verifier.verify(context(true, false, true)));
  }
}
