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

package org.idp.server.core.adapters.datasource.oidc.request;

import java.util.Map;
import org.idp.server.core.openid.identity.id_token.RequestedClaimsPayload;
import org.idp.server.core.openid.oauth.AuthorizationProfile;
import org.idp.server.core.openid.oauth.configuration.client.ClientAttributes;
import org.idp.server.core.openid.oauth.rar.AuthorizationDetails;
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;
import org.idp.server.core.openid.oauth.request.AuthorizationRequestBuilder;
import org.idp.server.core.openid.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.openid.oauth.type.extension.ExpiresAt;
import org.idp.server.core.openid.oauth.type.oauth.*;
import org.idp.server.core.openid.oauth.type.oidc.*;
import org.idp.server.core.openid.oauth.type.pkce.CodeChallenge;
import org.idp.server.core.openid.oauth.type.pkce.CodeChallengeMethod;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

class ModelConverter {

  static JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  static AuthorizationRequest convert(Map<String, String> stringMap) {
    AuthorizationRequestBuilder builder = new AuthorizationRequestBuilder();
    builder.add(new AuthorizationRequestIdentifier(stringMap.get("id")));
    builder.add(new TenantIdentifier(stringMap.get("tenant_id")));
    builder.add(AuthorizationProfile.valueOf(stringMap.get("profile")));
    builder.add(new Scopes(stringMap.get("scopes")));
    builder.add(ResponseType.valueOf(stringMap.get("response_type")));
    builder.add(new RequestedClientId(stringMap.get("client_id")));
    builder.add(convertClient(stringMap.get("client_payload")));
    builder.add(new RedirectUri(stringMap.get("redirect_uri")));
    builder.add(new State(stringMap.get("state")));
    builder.add(ResponseMode.of(stringMap.get("response_mode")));
    builder.add(new Nonce(stringMap.get("nonce")));
    builder.add(Display.of(stringMap.get("display")));
    builder.add(Prompts.of(stringMap.get("prompts")));
    builder.add(new MaxAge(stringMap.get("max_age")));
    builder.add(new UiLocales(stringMap.get("ui_locales")));
    builder.add(new IdTokenHint(stringMap.get("id_token_hint")));
    builder.add(new LoginHint(stringMap.get("login_hint")));
    builder.add(new AcrValues(stringMap.get("acr_values")));
    builder.add(new ClaimsValue(stringMap.get("claims_value")));
    builder.add(new RequestObject(stringMap.get("request_object")));
    builder.add(new RequestUri(stringMap.get("request_uri")));
    builder.add(convertClaimsPayload(stringMap.get("claims_value")));
    builder.add(new CodeChallenge(stringMap.get("code_challenge")));
    builder.add(CodeChallengeMethod.of(stringMap.get("code_challenge_method")));
    builder.add(AuthorizationDetails.fromString(stringMap.get("authorization_details")));
    builder.add(convertCustomParams(stringMap.get("custom_params")));
    builder.add(new ExpiresIn(stringMap.get("expires_in")));
    builder.add(new ExpiresAt(stringMap.get("expires_at")));
    return builder.build();
  }

  private static ClientAttributes convertClient(String value) {
    if (value == null || value.isEmpty()) {
      return new ClientAttributes();
    }
    return jsonConverter.read(value, ClientAttributes.class);
  }

  private static RequestedClaimsPayload convertClaimsPayload(String value) {
    if (value == null || value.isEmpty()) {
      return new RequestedClaimsPayload();
    }
    try {
      return jsonConverter.read(value, RequestedClaimsPayload.class);
    } catch (Exception exception) {
      return new RequestedClaimsPayload();
    }
  }

  private static CustomParams convertCustomParams(String value) {
    if (value == null || value.isEmpty()) {
      return new CustomParams();
    }
    try {

      Map<String, String> read = jsonConverter.read(value, Map.class);

      return new CustomParams(read);
    } catch (Exception exception) {
      return new CustomParams();
    }
  }
}
