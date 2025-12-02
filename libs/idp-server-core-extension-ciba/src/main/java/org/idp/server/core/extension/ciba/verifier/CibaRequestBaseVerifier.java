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

package org.idp.server.core.extension.ciba.verifier;

import org.idp.server.core.extension.ciba.CibaRequestContext;
import org.idp.server.core.extension.ciba.exception.BackchannelAuthenticationBadRequestException;
import org.idp.server.core.openid.oauth.type.ciba.BindingMessage;
import org.idp.server.core.openid.oauth.type.oauth.ClientAuthenticationType;
import org.idp.server.core.openid.oauth.type.oauth.GrantType;

public class CibaRequestBaseVerifier {

  /**
   * Maximum length for binding_message.
   *
   * <p>Per CIBA Core Section 7.1, the binding_message SHOULD be relatively short and use a limited
   * set of plain text characters. This limit ensures the message can be displayed on authentication
   * devices with limited screen space.
   */
  private static final int BINDING_MESSAGE_MAX_LENGTH = 6;

  public void verify(CibaRequestContext context) {
    throwExceptionIfUnSupportedGrantType(context);
    throwExceptionIfPublicClient(context);
    throwExceptionIfNotContainsOpenidScope(context);
    throwExceptionIfNotContainsAnyHint(context);
    throwExceptionIfNotContainsUserCode(context);
    throwExceptionIfInvalidBindingMessage(context);
  }

  void throwExceptionIfUnSupportedGrantType(CibaRequestContext context) {
    if (!context.isSupportedGrantTypeWithServer(GrantType.ciba)) {
      throw new BackchannelAuthenticationBadRequestException(
          "unauthorized_client", "authorization server is unsupported ciba grant");
    }
    if (!context.isSupportedGrantTypeWithClient(GrantType.ciba)) {
      throw new BackchannelAuthenticationBadRequestException(
          "unauthorized_client", "client is unauthorized ciba grant");
    }
  }

  void throwExceptionIfNotContainsOpenidScope(CibaRequestContext context) {
    if (!context.hasOpenidScope()) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_scope",
          "backchannel request does not contains openid scope. OpenID Connect implements authentication as an extension to OAuth 2.0 by including the openid scope value in the authorization requests.");
    }
  }

  void throwExceptionIfNotContainsAnyHint(CibaRequestContext context) {
    if (!context.hasAnyHint()) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request",
          "backchannel request does not have any hint, must contains login_hint or login_hint_token or id_token_hint");
    }
  }

  void throwExceptionIfNotContainsUserCode(CibaRequestContext context) {
    if (!context.hasUserCode() && context.requiredBackchannelAuthUserCode()) {
      throw new BackchannelAuthenticationBadRequestException(
          "missing_user_code", "user_code is required for this id-provider.");
    }
  }

  /**
   * Public Client Prohibition
   *
   * <p>CIBA requires confidential clients for secure backchannel authentication. Public clients
   * cannot securely authenticate themselves in the backchannel flow.
   */
  void throwExceptionIfPublicClient(CibaRequestContext context) {
    ClientAuthenticationType authenticationType = context.clientAuthenticationType();

    if (authenticationType.isNone()) {
      throw new BackchannelAuthenticationBadRequestException(
          "unauthorized_client",
          "Public clients are not allowed in CIBA. Use confidential client with proper authentication.");
    }
  }

  /**
   * Validates the binding_message parameter.
   *
   * <p>Per CIBA Core Section 7.1, the binding_message is intended to be displayed on both the
   * consumption device and the authentication device. It SHOULD be relatively short and use a
   * limited set of plain text characters.
   *
   * <p>Per CIBA Core Section 13, if the binding_message is invalid or unacceptable, the
   * invalid_binding_message error MUST be returned.
   */
  void throwExceptionIfInvalidBindingMessage(CibaRequestContext context) {
    BindingMessage bindingMessage = context.backchannelAuthenticationRequest().bindingMessage();
    if (!bindingMessage.exists()) {
      return;
    }

    if (bindingMessage.value().length() > BINDING_MESSAGE_MAX_LENGTH) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_binding_message",
          String.format(
              "binding_message exceeds maximum length of %d characters",
              BINDING_MESSAGE_MAX_LENGTH));
    }
  }
}
