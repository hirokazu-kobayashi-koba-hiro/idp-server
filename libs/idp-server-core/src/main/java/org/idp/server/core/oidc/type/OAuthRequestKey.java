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

package org.idp.server.core.oidc.type;

/** OAuthRequestKey */
public enum OAuthRequestKey {
  scope,
  response_type,
  client_id,
  redirect_uri,
  state,
  response_mode,
  nonce,
  display,
  prompt,
  max_age,
  ui_locales,
  id_token_hint,
  login_hint,
  acr_values,
  claims,
  request,
  request_uri,
  code_challenge,
  code_challenge_method,
  code_verifier,
  code,
  grant_type,
  client_secret,
  token,
  token_type_hint,
  client_assertion,
  client_assertion_type,
  refresh_token,
  client_notification_token,
  login_hint_token,
  binding_message,
  user_code,
  requested_expiry,
  auth_req_id,
  username,
  password,
  authorization_details,
  wallet_issuer,
  user_hint,
  issuer_state,
  // pre-authorized_code
  pre_authorized_code,
  user_pin,
  format,
  credential_definition,
  proof,
  client_id_scheme,
  client_metadata,
  client_metadata_uri,
  transaction_id,
  // RP-Initiated Logout
  post_logout_redirect_uri,
  logout_hint;

  public static boolean contains(String key) {
    for (OAuthRequestKey keys : OAuthRequestKey.values()) {
      if (keys.name().equals(key)) {
        return true;
      }
    }
    return false;
  }
}
