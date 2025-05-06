package org.idp.server.basic.type;

/** OAuthRequestKey */
public enum OAuthRequestKey {
  scope, response_type, client_id, redirect_uri, state, response_mode, nonce, display, prompt, max_age, ui_locales, id_token_hint, login_hint, acr_values, claims, request, request_uri, code_challenge, code_challenge_method, code_verifier, code, grant_type, client_secret, token, token_type_hint, client_assertion, client_assertion_type, refresh_token, client_notification_token, login_hint_token, binding_message, user_code, requested_expiry, auth_req_id, username, password, authorization_details, wallet_issuer, user_hint, issuer_state,
  // pre-authorized_code
  pre_authorized_code, user_pin, format, credential_definition, proof, client_id_scheme, client_metadata, client_metadata_uri, transaction_id,
  // RP-Initiated Logout
  post_logout_redirect_uri, logout_hint;

  public static boolean contains(String key) {
    for (OAuthRequestKey keys : OAuthRequestKey.values()) {
      if (keys.name().equals(key)) {
        return true;
      }
    }
    return false;
  }
}
