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
package org.idp.server.account_linking.io;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.Map;
import org.idp.server.account_linking.ExternalAccessToken;
import org.idp.server.account_linking.ExternalAccessTokenAcquisition;
import org.idp.server.account_linking.ExternalIdpProvider;
import org.idp.server.account_linking.exception.LinkedExternalAccountTokenException;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.junit.jupiter.api.Test;

class LinkedExternalAccountTokenResultTest {

  static final ExternalIdpProvider PROVIDER = new ExternalIdpProvider("example-idp");

  @Test
  void carriesTheProviderAndTheErrorOfARefusedRefresh() {
    LinkedExternalAccountTokenResult result =
        new LinkedExternalAccountTokenResult(null, false, true, PROVIDER, "relink_required");

    assertEquals(
        Map.of("provider", "example-idp", "error", "relink_required"), result.eventDetails());
  }

  @Test
  void carriesOnlyTheProviderOfARetrieval() {
    LinkedExternalAccountTokenResult result =
        new LinkedExternalAccountTokenResult(null, true, false, PROVIDER, null);

    assertEquals(Map.of("provider", "example-idp"), result.eventDetails());
  }

  @Test
  void leavesTheProviderOutBeforeTheAccountIsFound() {
    LinkedExternalAccountTokenResult result =
        new LinkedExternalAccountTokenResult(null, false, false, null, "invalid_token");

    assertEquals(Map.of("error", "invalid_token"), result.eventDetails());
  }

  @Test
  void aRetrievalHandsOutTheTokenAndSaysWhetherItWasRefreshed() {
    ExternalAccessTokenAcquisition acquisition =
        new ExternalAccessTokenAcquisition(
            new ExternalAccessToken("external-token", LocalDateTime.now().plusMinutes(5), null),
            true);

    LinkedExternalAccountTokenResult result =
        LinkedExternalAccountTokenResult.retrieved(
            acquisition, PROVIDER, new RequestedClientId("rp-client"), new User().setSub("user"));

    assertEquals(200, result.result().statusCode());
    assertEquals("external-token", result.result().contents().get("access_token"));
    assertTrue(result.refreshed());
    assertFalse(result.relinkRequired());
    assertEquals(Map.of("provider", "example-idp"), result.eventDetails());
  }

  @Test
  void aRefusalCarriesTheErrorAndWhetherTheUserHasToLinkAgain() {
    LinkedExternalAccountTokenResult result =
        LinkedExternalAccountTokenResult.refused(
            LinkedExternalAccountTokenException.relinkRequired("Link the account again."),
            new RequestedClientId("rp-client"),
            new User().setSub("user"),
            PROVIDER);

    assertEquals(400, result.result().statusCode());
    assertEquals("relink_required", result.result().contents().get("error"));
    assertFalse(result.refreshed());
    assertTrue(result.relinkRequired());
    assertEquals(
        Map.of("provider", "example-idp", "error", "relink_required"), result.eventDetails());
  }
}
