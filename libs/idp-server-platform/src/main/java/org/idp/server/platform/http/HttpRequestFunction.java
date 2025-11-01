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

package org.idp.server.platform.http;

import java.net.http.HttpRequest;

/**
 * Functional interface for executing HTTP requests.
 *
 * <p>This interface allows retry logic to be decoupled from the actual HTTP request execution,
 * enabling flexible retry strategies for different types of HTTP operations (basic, OAuth, etc.).
 *
 * <h3>Usage Example</h3>
 *
 * <pre>{@code
 * // Basic execution
 * HttpRequestFunction basicExecution = httpRequestExecutor::execute;
 *
 * // OAuth execution
 * HttpRequestFunction oauthExecution = req -> httpRequestExecutor.executeWithOAuth(req, oauthConfig);
 *
 * // Use with retry strategy
 * HttpRequestResult result = retryStrategy.executeWithRetry(request, retryConfig, basicExecution);
 * }</pre>
 */
@FunctionalInterface
public interface HttpRequestFunction {

  /**
   * Executes an HTTP request and returns the result.
   *
   * @param request the HTTP request to execute
   * @return the result of the HTTP request execution
   */
  HttpRequestResult execute(HttpRequest request);
}
