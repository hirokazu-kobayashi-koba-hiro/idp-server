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

package org.idp.server.core.extension.ciba.clientnotification;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.idp.server.basic.http.HttpClientFactory;
import org.idp.server.core.extension.ciba.gateway.ClientNotificationGateway;
import org.idp.server.platform.log.LoggerWrapper;

public class NotificationClient implements ClientNotificationGateway {

  LoggerWrapper log = LoggerWrapper.getLogger(NotificationClient.class);
  HttpClient httpClient;

  public NotificationClient() {
    this.httpClient = HttpClientFactory.defaultClient();
  }

  @Override
  public void notify(ClientNotificationRequest clientNotificationRequest) {
    try {
      log.info("notification endpoint: " + clientNotificationRequest.endpoint());
      log.info("notification body: " + clientNotificationRequest.body());

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(new URI(clientNotificationRequest.endpoint()))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(clientNotificationRequest.body()))
              .build();

      httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (Exception exception) {
      // TODO
      log.error(exception.getMessage());
    }
  }
}
