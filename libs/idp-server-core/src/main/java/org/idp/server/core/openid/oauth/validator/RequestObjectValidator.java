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

package org.idp.server.core.openid.oauth.validator;

import java.util.Map;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.request.OAuthRequestParameters;
import org.idp.server.core.openid.oauth.type.OAuthRequestKey;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Request Object Validator
 *
 * <p>Validates request object (JWT) payload parameters according to OAuth 2.0 and related
 * specifications.
 *
 * <p>This validator performs early validation of request object parameters before they are
 * processed by the authorization flow.
 */
public class RequestObjectValidator {

  Tenant tenant;
  OAuthRequestParameters parameters;
  AuthorizationServerConfiguration authorizationServerConfiguration;
  ClientConfiguration clientConfiguration;

  public RequestObjectValidator(
      Tenant tenant,
      OAuthRequestParameters parameters,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {
    this.tenant = tenant;
    this.parameters = parameters;
    this.authorizationServerConfiguration = authorizationServerConfiguration;
    this.clientConfiguration = clientConfiguration;
  }

  /**
   * Validate request object payload
   *
   * @param payload JWT claims (request object payload)
   */
  public void validate(Map<String, Object> payload) {
    throwExceptionIfInvalidAuthorizationDetails(payload);
  }

  /**
   * RFC 9396 Section 2 & Section 5 - Authorization Details Validation
   *
   * <p>Delegates to {@link AuthorizationDetailsValidator} for common validation logic.
   *
   * @param payload JWT claims (request object payload)
   */
  void throwExceptionIfInvalidAuthorizationDetails(Map<String, Object> payload) {
    if (!payload.containsKey(OAuthRequestKey.authorization_details.name())) {
      return;
    }

    Object object = payload.get(OAuthRequestKey.authorization_details.name());
    AuthorizationDetailsValidator.validate(object, tenant);
  }
}
