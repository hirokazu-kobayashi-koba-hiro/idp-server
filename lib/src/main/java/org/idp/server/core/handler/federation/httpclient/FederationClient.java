package org.idp.server.core.handler.federation.httpclient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.logging.Logger;
import org.idp.server.core.basic.http.HttpClientFactory;
import org.idp.server.core.basic.http.QueryParams;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.federation.*;
import org.idp.server.core.handler.ciba.httpclient.NotificationClient;

public class FederationClient implements FederationGateway {

  Logger log = Logger.getLogger(NotificationClient.class.getName());
  HttpClient httpClient;
  JsonConverter jsonConverter;

  public FederationClient() {
    this.httpClient = HttpClientFactory.defaultClient();
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  @Override
  public FederationTokenResponse requestToken(FederationTokenRequest federationTokenRequest) {
    try {

      QueryParams queryParams = new QueryParams(federationTokenRequest.toMap());

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(new URI(federationTokenRequest.endpoint()))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .POST(HttpRequest.BodyPublishers.ofString(queryParams.params()))
              .build();

      HttpResponse<String> httpResponse =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      String body = httpResponse.body();
      log.info("token response:" + body);

      Map map = jsonConverter.read(body, Map.class);

      return new FederationTokenResponse(map);
    } catch (Exception e) {
      log.severe(e.getMessage());
      throw new RuntimeException(e);
    }
  }

  @Override
  public FederationUserinfoResponse requestUserInfo(
      FederationUserinfoRequest federationUserinfoRequest) {
    try {

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(new URI(federationUserinfoRequest.endpoint()))
              .header("Content-Type", "application/json")
              .header(
                  "Authorization",
                  String.format("Bearer %s", federationUserinfoRequest.accessToken()))
              .GET()
              .build();

      HttpResponse<String> httpResponse =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      String body = httpResponse.body();
      log.info("userinfo response:" + body);

      Map map = jsonConverter.read(body, Map.class);


      return new FederationUserinfoResponse(map);
    } catch (Exception e) {
      log.severe(e.getMessage());
      throw new RuntimeException(e);
    }
  }
}
