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

import org.idp.server.core.extension.ciba.CibaRequestContext;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.extension.ciba.response.BackchannelAuthenticationErrorResponse;
import org.idp.server.core.extension.ciba.user.UserHint;
import org.idp.server.core.extension.ciba.user.UserHintRelatedParams;
import org.idp.server.core.extension.ciba.user.UserHintType;
import org.idp.server.core.openid.oauth.type.ContentType;

public class CibaRequestResult {
  CibaRequestStatus status;
  CibaRequestContext cibaRequestContext;
  BackchannelAuthenticationErrorResponse errorResponse;
  ContentType contentType;

  public CibaRequestResult(CibaRequestStatus status, CibaRequestContext cibaRequestContext) {
    this.status = status;
    this.cibaRequestContext = cibaRequestContext;
    this.errorResponse = new BackchannelAuthenticationErrorResponse();
    // FIXME consider
    this.contentType = ContentType.application_json;
  }

  public CibaRequestResult(
      CibaRequestStatus status, BackchannelAuthenticationErrorResponse errorResponse) {
    this.status = status;
    this.errorResponse = errorResponse;
    // FIXME consider
    this.contentType = ContentType.application_json;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public BackchannelAuthenticationRequest request() {
    return cibaRequestContext.backchannelAuthenticationRequest();
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
    return errorResponse.contents();
  }

  public boolean isOK() {
    return status.isOK();
  }

  public CibaRequestContext context() {
    return cibaRequestContext;
  }

  public CibaRequestResponse toErrorResponse() {
    return new CibaRequestResponse(status, errorResponse);
  }

  public UserHintType userHintType() {
    return cibaRequestContext.userHintType();
  }

  public UserHint userhint() {
    return cibaRequestContext.userHint();
  }

  public UserHintRelatedParams userHintRelatedParams() {
    return cibaRequestContext.userHintRelatedParams();
  }
}
