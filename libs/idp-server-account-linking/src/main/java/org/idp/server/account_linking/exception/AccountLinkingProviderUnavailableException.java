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
package org.idp.server.account_linking.exception;

import org.idp.server.platform.exception.BadRequestException;

/**
 * Raised when a link is started to a provider that is not configured, or that an operator has
 * disabled.
 *
 * <p>Unlike the other refusals of {@code link}, this one happens in service, not only while an RP
 * is being built, so it carries a code an RP can show the user: the same one {@code complete} and
 * token retrieval answer when the provider goes away later.
 */
public class AccountLinkingProviderUnavailableException extends BadRequestException {

  public AccountLinkingProviderUnavailableException(String message) {
    super(message);
  }
}
