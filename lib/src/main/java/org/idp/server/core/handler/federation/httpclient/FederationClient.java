package org.idp.server.core.handler.federation.httpclient;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.logging.Logger;
import org.idp.server.core.basic.http.HttpClientErrorException;
import org.idp.server.core.basic.http.HttpClientFactory;
import org.idp.server.core.basic.http.HttpNetworkErrorException;
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

      HttpRequest.Builder builder =
          HttpRequest.newBuilder()
              .uri(new URI(federationTokenRequest.endpoint()))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .header("Accept", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(queryParams.params()));

      if (federationTokenRequest.isClientSecretBasic()) {
        builder.header("Authorization", federationTokenRequest.basicAuthenticationValue());
      }

      HttpRequest request = builder.build();

      HttpResponse<String> httpResponse =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      String body = httpResponse.body();
      log.info("token response:" + body);

      validateResponse(httpResponse, body);

      Map map = jsonConverter.read(body, Map.class);

      return new FederationTokenResponse(map);
    } catch (IOException | InterruptedException | URISyntaxException e) {
      log.severe(e.getMessage());
      throw new HttpNetworkErrorException("unexpected network error", e);
    }
  }

  @Override
  public FederationJwksResponse getJwks(FederationJwksRequest federationJwksRequest) {

    try {

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(new URI(federationJwksRequest.endpoint()))
              .header("Content-Type", "application/json")
              .header("Accept", "application/json")
              .GET()
              .build();

      HttpResponse<String> httpResponse =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      String body = httpResponse.body();
      log.info("jwks response:" + body);

      validateResponse(httpResponse, body);

      return new FederationJwksResponse(body);
    } catch (IOException | InterruptedException | URISyntaxException e) {
      log.severe(e.getMessage());
      throw new HttpNetworkErrorException("unexpected network error", e);
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
              .header("Accept", "application/json")
              .header(
                  "Authorization",
                  String.format("Bearer %s", federationUserinfoRequest.accessToken()))
              .GET()
              .build();

      HttpResponse<String> httpResponse =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      String body = httpResponse.body();
      log.info("userinfo response:" + body);

      validateResponse(httpResponse, body);

      Map map = jsonConverter.read(body, Map.class);

      return new FederationUserinfoResponse(map);
    } catch (IOException | InterruptedException | URISyntaxException e) {
      log.severe(e.getMessage());
      throw new HttpNetworkErrorException("unexpected network error", e);
    }
  }

  @Override
  public FederationUserinfoResponse requestFacebookSpecificUerInfo(
      FederationUserinfoRequest federationUserinfoRequest) {
    try {

      QueryParams queryParams = new QueryParams();
      queryParams.add("fields", "id,name,email,picture");
      queryParams.add("access_token", federationUserinfoRequest.accessToken());
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(new URI(federationUserinfoRequest.endpoint() + "?" + queryParams.params()))
              .header("Content-Type", "application/json")
              .header("Accept", "application/json")
              .GET()
              .build();

      HttpResponse<String> httpResponse =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      String body = httpResponse.body();
      log.info("userinfo response:" + body);

      validateResponse(httpResponse, body);

      Map map = jsonConverter.read(body, Map.class);

      return new FederationUserinfoResponse(map);
    } catch (IOException | InterruptedException | URISyntaxException e) {
      log.severe(e.getMessage());
      throw new HttpNetworkErrorException("unexpected network error", e);
    }
  }

  private void validateResponse(HttpResponse<String> httpResponse, String body) {
    if (httpResponse.statusCode() >= 400 && httpResponse.statusCode() < 500) {
      throw new HttpClientErrorException(body, httpResponse.statusCode());
    }

    if (httpResponse.statusCode() >= 500) {
      throw new HttpClientErrorException(body, httpResponse.statusCode());
    }
  }
}
