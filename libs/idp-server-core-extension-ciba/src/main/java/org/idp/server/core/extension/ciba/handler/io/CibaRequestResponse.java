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

import org.idp.server.core.extension.ciba.response.BackchannelAuthenticationErrorResponse;
import org.idp.server.core.extension.ciba.response.BackchannelAuthenticationResponse;
import org.idp.server.core.oidc.type.ContentType;

public class CibaRequestResponse {
  CibaRequestStatus status;
  BackchannelAuthenticationResponse backchannelAuthenticationResponse;
  BackchannelAuthenticationErrorResponse errorResponse;
  ContentType contentType;

  public CibaRequestResponse(
      CibaRequestStatus status,
      BackchannelAuthenticationResponse backchannelAuthenticationResponse) {
    this.status = status;
    this.backchannelAuthenticationResponse = backchannelAuthenticationResponse;
    this.errorResponse = new BackchannelAuthenticationErrorResponse();
    // FIXME consider
    this.contentType = ContentType.application_json;
  }

  public CibaRequestResponse(
      CibaRequestStatus status, BackchannelAuthenticationErrorResponse errorResponse) {
    this.status = status;
    this.errorResponse = errorResponse;
    // FIXME consider
    this.contentType = ContentType.application_json;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public BackchannelAuthenticationErrorResponse errorResponse() {
    return errorResponse;
  }

  public ContentType contentType() {
    return contentType;
  }

  public String contentTypeValue() {
    return contentType.value();
  }

  public String contents() {
    if (status.isOK()) {
      return backchannelAuthenticationResponse.contents();
    }
    return errorResponse.contents();
  }

  public boolean isOK() {
    return status.isOK();
  }
}
