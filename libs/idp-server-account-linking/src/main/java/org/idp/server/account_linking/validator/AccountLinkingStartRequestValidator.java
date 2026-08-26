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

package org.idp.server.account_linking.validator;

import java.util.List;
import org.idp.server.account_linking.exception.AccountLinkingInvalidRequestException;
import org.idp.server.account_linking.io.AccountLinkingStartRequest;

/**
 * Checks the link start request while the calling client is still known.
 *
 * <p>{@code redirect_uri} is the flow's only redirect target and nothing later in the flow can
 * establish which client asked for it, so the allow list has to be applied here.
 */
public class AccountLinkingStartRequestValidator {

  public void validate(AccountLinkingStartRequest request, List<String> allowedRedirectUris) {
    String redirectUri = request.redirectUri();

    if (redirectUri == null || redirectUri.isEmpty()) {
      throw new AccountLinkingInvalidRequestException("redirect_uri is required.");
    }

    if (!allowedRedirectUris.contains(redirectUri)) {
      throw new AccountLinkingInvalidRequestException(
          "redirect_uri is not registered for this client.");
    }
  }
}
