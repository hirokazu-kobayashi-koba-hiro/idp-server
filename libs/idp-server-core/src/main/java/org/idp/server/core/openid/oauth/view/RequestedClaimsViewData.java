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

package org.idp.server.core.openid.oauth.view;

import java.util.LinkedHashMap;
import java.util.Map;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.text.HtmlEscaper;

/**
 * Builds the {@code requested_claims} entry of the authorization view-data: for each requested
 * claim that carries a {@code purpose} (OIDC4IDA Implementer's Draft §5.1; dropped in OIDC4IDA 1.0
 * final, still exercised by the eKYC OP conformance suite IA-9), the claim name maps to its {@code
 * essential} flag and its {@code purpose}. The purpose is HTML-escaped because it is RP-supplied
 * text shown on the consent screen (XSS prevention).
 *
 * <p>Covers the standard {@code id_token} / {@code userinfo} claims. Purposes nested inside a
 * {@code verified_claims} request are not yet surfaced here.
 */
public class RequestedClaimsViewData {

  private RequestedClaimsViewData() {}

  /** Returns {@code claim_name -> {essential, purpose}}; empty when no purpose was requested. */
  public static Map<String, Object> from(String claimsJson) {
    Map<String, Object> result = new LinkedHashMap<>();
    if (claimsJson == null || claimsJson.isBlank()) {
      return result;
    }
    JsonNodeWrapper root = JsonNodeWrapper.fromString(claimsJson);
    collectPurposes(root.getValueAsJsonNode("id_token"), result);
    collectPurposes(root.getValueAsJsonNode("userinfo"), result);
    return result;
  }

  private static void collectPurposes(JsonNodeWrapper claimsMember, Map<String, Object> result) {
    if (claimsMember == null || !claimsMember.exists() || !claimsMember.isObject()) {
      return;
    }
    for (String name : claimsMember.fieldNamesAsList()) {
      if (name.equals("verified_claims")) {
        continue;
      }
      JsonNodeWrapper claim = claimsMember.getValueAsJsonNode(name);
      if (claim == null || !claim.isObject() || !claim.contains("purpose")) {
        continue;
      }
      String purpose = claim.getValueOrEmptyAsString("purpose");
      if (purpose.isEmpty()) {
        continue;
      }
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("essential", claim.optValueAsBoolean("essential", false));
      entry.put("purpose", HtmlEscaper.escape(purpose));
      result.put(name, entry);
    }
  }
}
