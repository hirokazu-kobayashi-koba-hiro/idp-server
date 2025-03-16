package org.idp.server.core.adapters.httpclient.ciba;

import org.idp.server.core.basic.http.HttpClientFactory;
import org.idp.server.core.ciba.clientnotification.ClientNotificationRequest;
import org.idp.server.core.ciba.gateway.ClientNotificationGateway;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.Logger;

public class NotificationClient implements ClientNotificationGateway {

  Logger log = Logger.getLogger(NotificationClient.class.getName());
  HttpClient httpClient;

  public NotificationClient() {
    this.httpClient = HttpClientFactory.defaultClient();
  }

  @Override
  public void notify(ClientNotificationRequest clientNotificationRequest) {
    try {
      log.info("notification endpoint: " + clientNotificationRequest.endpoint());
      log.info("notification body: " + clientNotificationRequest.body());
      log.info("notification token: " + clientNotificationRequest.token());
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(new URI(clientNotificationRequest.endpoint()))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(clientNotificationRequest.body()))
              .build();
      httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (Exception exception) {
      // TODO
      log.severe(exception.getMessage());
    }
  }
}
