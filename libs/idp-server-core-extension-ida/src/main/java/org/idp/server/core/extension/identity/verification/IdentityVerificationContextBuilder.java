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

package org.idp.server.core.extension.identity.verification;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplication;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.type.RequestAttributes;

public class IdentityVerificationContextBuilder {

  private IdentityVerificationContext previousContext;
  private IdentityVerificationRequest request;
  private RequestAttributes requestAttributes;
  private User user;
  private IdentityVerificationApplication application;
  private Map<String, Object> additionalParams;
  private Map<String, Object> executionResult;

  public IdentityVerificationContextBuilder previousContext(
      IdentityVerificationContext previousContext) {
    this.previousContext = previousContext;
    return this;
  }

  public IdentityVerificationContextBuilder request(IdentityVerificationRequest request) {
    this.request = request;
    return this;
  }

  public IdentityVerificationContextBuilder requestAttributes(RequestAttributes requestAttributes) {
    this.requestAttributes = requestAttributes;
    return this;
  }

  public IdentityVerificationContextBuilder user(User user) {
    this.user = user;
    return this;
  }

  public IdentityVerificationContextBuilder application(
      IdentityVerificationApplication application) {
    this.application = application;
    return this;
  }

  public IdentityVerificationContextBuilder additionalParams(Map<String, Object> params) {
    this.additionalParams = params;
    return this;
  }

  public IdentityVerificationContextBuilder executionResult(Map<String, Object> executionResult) {
    this.executionResult = executionResult;
    return this;
  }

  public IdentityVerificationContext build() {
    Map<String, Object> context = new HashMap<>();
    if (previousContext != null) {
      context.putAll(previousContext.toMap());
    }
    if (request != null) {
      context.put("request_body", request.toMap());
    }
    if (requestAttributes != null) {
      context.put("request_attributes", requestAttributes.toMap());
    }
    if (user != null) {
      context.put("user", user.toMap());
    }
    if (application != null && application.exists()) {
      context.put("application", application.toMap());
    }
    if (additionalParams != null) {
      context.putAll(additionalParams);
    }
    if (executionResult != null) {
      context.putAll(executionResult);
    }

    return new IdentityVerificationContext(context);
  }
}
