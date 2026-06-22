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

package org.idp.server.platform.notification.sms;

import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.http.HttpRequestExecutor;

/**
 * Factory for creating {@link HttpRequestSmsSender} instances with dependency injection support.
 * This factory enables OAuth token caching by injecting {@link HttpRequestExecutor} from the DI
 * container. It is the SMS counterpart of {@code HttpRequestEmailSenderFactory}.
 *
 * <p>Benefits of using this factory:
 *
 * <ul>
 *   <li>OAuth token caching support (performance improvement)
 *   <li>Tenant/service-specific cache configuration
 *   <li>Proper dependency injection integration
 * </ul>
 */
public class HttpRequestSmsSenderFactory implements SmsSenderFactory {

  @Override
  public SmsSender create(ApplicationComponentDependencyContainer container) {
    // Resolve HttpRequestExecutor from DI container (with OAuth caching support)
    HttpRequestExecutor httpRequestExecutor = container.resolve(HttpRequestExecutor.class);

    // Create SmsSender with DI-injected dependencies
    return new HttpRequestSmsSender(httpRequestExecutor);
  }

  @Override
  public String type() {
    return "http_request";
  }

  @Override
  public SmsSenderType smsSenderType() {
    return new SmsSenderType("http_request");
  }
}
