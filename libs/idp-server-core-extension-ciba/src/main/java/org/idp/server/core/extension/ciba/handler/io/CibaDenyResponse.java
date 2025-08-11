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

package org.idp.server.core.extension.ciba.handler.io;

import org.idp.server.core.openid.oauth.type.oauth.Error;
import org.idp.server.core.openid.oauth.type.oauth.ErrorDescription;

public class CibaDenyResponse {
  CibaDenyStatus status;
  Error error;
  ErrorDescription errorDescription;

  public CibaDenyResponse(CibaDenyStatus status) {
    this.status = status;
  }

  public CibaDenyResponse(CibaDenyStatus status, Error error, ErrorDescription errorDescription) {
    this.status = status;
    this.error = error;
    this.errorDescription = errorDescription;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public boolean isError() {
    return !status.isOK();
  }

  public Error error() {
    return error;
  }

  public ErrorDescription errorDescription() {
    return errorDescription;
  }
}
