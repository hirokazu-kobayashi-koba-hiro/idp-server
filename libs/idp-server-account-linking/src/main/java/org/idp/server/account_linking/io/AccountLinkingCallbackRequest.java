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

import org.idp.server.account_linking.AccountLinkingState;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Everything the callback works from: what the external IdP sent back, and the binding secret the
 * browser presented.
 */
public class AccountLinkingCallbackRequest {

  Tenant tenant;
  AccountLinkingState state;
  String code;
  String error;
  String errorDescription;
  String browserBindingSecret;

  public AccountLinkingCallbackRequest(
      Tenant tenant,
      AccountLinkingState state,
      String code,
      String error,
      String errorDescription,
      String browserBindingSecret) {
    this.tenant = tenant;
    this.state = state;
    this.code = code;
    this.error = error;
    this.errorDescription = errorDescription;
    this.browserBindingSecret = browserBindingSecret;
  }

  public Tenant tenant() {
    return tenant;
  }

  public AccountLinkingState state() {
    return state;
  }

  public String code() {
    return code;
  }

  public String error() {
    return error;
  }

  public String errorDescription() {
    return errorDescription;
  }

  public boolean hasError() {
    return error != null && !error.isEmpty();
  }

  public String browserBindingSecret() {
    return browserBindingSecret;
  }
}
