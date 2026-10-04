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

package org.idp.server.core.openid.authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.junit.jupiter.api.Test;

/** View hints only add to the authorization view, so one interactor failing must not fail it. */
class AuthenticationInteractorsViewHintsTest {

  @Test
  void leavesOutTheHintsOfAnInteractorThatFailsAndKeepsTheOthers() {
    AuthenticationInteractors interactors =
        new AuthenticationInteractors(
            Map.of(
                new AuthenticationInteractionType("broken"),
                new HintingInteractor("broken", null),
                new AuthenticationInteractionType("working"),
                new HintingInteractor("working", Map.of("inputs", List.of("code")))));

    Map<String, Map<String, Object>> hints =
        interactors.viewHints(tenant(), List.of("broken", "working"));

    assertEquals(Map.of("working", Map.of("inputs", List.of("code"))), hints);
  }

  private static Tenant tenant() {
    return new Tenant(
        new TenantIdentifier("67e7eae6-62b0-4500-9eff-87459f63fc66"),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        true);
  }

  /** Answers {@code hints}, or throws when they are null. */
  private record HintingInteractor(String method, Map<String, Object> hints)
      implements AuthenticationInteractor {

    @Override
    public AuthenticationInteractionType type() {
      return new AuthenticationInteractionType(method);
    }

    @Override
    public Map<String, Object> viewHints(Tenant tenant) {
      if (hints == null) {
        throw new IllegalStateException("broken configuration");
      }
      return hints;
    }

    @Override
    public AuthenticationInteractionRequestResult interact(
        Tenant tenant,
        AuthenticationTransaction transaction,
        AuthenticationInteractionType type,
        AuthenticationInteractionRequest request,
        RequestAttributes requestAttributes,
        UserQueryRepository userQueryRepository) {
      throw new UnsupportedOperationException();
    }
  }
}
