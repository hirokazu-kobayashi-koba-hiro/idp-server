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

package org.idp.server.core.adapters.datasource.ciba.request;

import java.util.Map;
import org.idp.server.core.extension.ciba.CibaProfile;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestBuilder;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.openid.oauth.rar.AuthorizationDetails;
import org.idp.server.core.openid.oauth.type.ciba.*;
import org.idp.server.core.openid.oauth.type.extension.ExpiresAt;
import org.idp.server.core.openid.oauth.type.oauth.ExpiresIn;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.core.openid.oauth.type.oidc.AcrValues;
import org.idp.server.core.openid.oauth.type.oidc.IdTokenHint;
import org.idp.server.core.openid.oauth.type.oidc.LoginHint;
import org.idp.server.core.openid.oauth.type.oidc.RequestObject;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

class ModelConverter {

  static BackchannelAuthenticationRequest convert(Map<String, String> stringMap) {
    BackchannelAuthenticationRequestBuilder builder = new BackchannelAuthenticationRequestBuilder();
    builder.add(new BackchannelAuthenticationRequestIdentifier(stringMap.get("id")));
    builder.add(new TenantIdentifier(stringMap.get("tenant_id")));
    builder.add(CibaProfile.valueOf(stringMap.get("profile")));
    builder.add(BackchannelTokenDeliveryMode.valueOf(stringMap.get("delivery_mode")));
    builder.add(new Scopes(stringMap.get("scopes")));
    builder.add(new RequestedClientId(stringMap.get("client_id")));
    builder.add(new IdTokenHint(stringMap.get("id_token_hint")));
    builder.add(new LoginHint(stringMap.get("login_hint")));
    builder.add(new LoginHintToken(stringMap.get("login_hint_token")));
    builder.add(new AcrValues(stringMap.get("acr_values")));
    builder.add(new UserCode(stringMap.get("user_code")));
    builder.add(new ClientNotificationToken(stringMap.get("client_notification_token")));
    builder.add(new BindingMessage(stringMap.get("binding_message")));
    builder.add(new RequestedExpiry(stringMap.get("requested_expiry")));
    builder.add(new RequestObject(stringMap.get("request_object")));
    builder.add(AuthorizationDetails.fromString(stringMap.get("authorization_details")));
    builder.add(new ExpiresIn(stringMap.get("expires_in")));
    builder.add(new ExpiresAt(stringMap.get("expires_at")));
    return builder.build();
  }
}
