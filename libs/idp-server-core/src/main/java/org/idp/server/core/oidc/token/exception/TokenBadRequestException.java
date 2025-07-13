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

package org.idp.server.core.oidc.token.exception;

import org.idp.server.core.oidc.type.oauth.Error;
import org.idp.server.core.oidc.type.oauth.ErrorDescription;

public class TokenBadRequestException extends RuntimeException {

  String error;
  String errorDescription;

  public TokenBadRequestException(String errorDescription) {
    super(errorDescription);
    this.error = "invalid_request";
    this.errorDescription = errorDescription;
  }

  public TokenBadRequestException(String errorDescription, Throwable throwable) {
    super(errorDescription, throwable);
    this.error = "invalid_request";
    this.errorDescription = errorDescription;
  }

  public TokenBadRequestException(String error, String errorDescription) {
    super(errorDescription);
    this.error = error;
    this.errorDescription = errorDescription;
  }

  public Error error() {
    return new Error(error);
  }

  public ErrorDescription errorDescription() {
    return new ErrorDescription(errorDescription);
  }
}
