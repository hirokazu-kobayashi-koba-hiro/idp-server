package org.idp.server.core.basic.oauth;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.core.basic.http.HttpClientFactory;
import org.idp.server.core.basic.http.HttpNetworkErrorException;
import org.idp.server.core.basic.http.QueryParams;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.json.JsonNodeWrapper;
import org.idp.server.core.type.oauth.AccessTokenEntity;

public class ClientCredentialsAuthorizationResolver implements OAuthAuthorizationResolver {

  HttpClient httpClient;
  JsonConverter jsonConverter;
  Logger log = Logger.getLogger(ClientCredentialsAuthorizationResolver.class.getName());

  public ClientCredentialsAuthorizationResolver() {
    this.httpClient = HttpClientFactory.defaultClient();
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  @Override
  public AccessTokenEntity resolve(OAuthAuthorizationConfiguration config) {
    try {

      QueryParams queryParams = new QueryParams(config.toMap());

      HttpRequest.Builder builder =
          HttpRequest.newBuilder()
              .uri(new URI(config.tokenEndpoint()))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .header("Accept", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(queryParams.params()));

      if (config.isClientSecretBasic()) {
        builder.header("Authorization", config.basicAuthenticationValue());
      }

      HttpRequest request = builder.build();

      log.log(Level.FINE, "Request headers: {0}", request.headers());
      if (request.bodyPublisher().isPresent()) {
        log.log(Level.FINE, "Request body: {0}", request.bodyPublisher().get());
      }

      HttpResponse<String> httpResponse =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      String body = httpResponse.body();

      log.log(Level.FINE, "Response status: {0}", httpResponse.statusCode());
      log.log(Level.FINE, "Response body: {0}", httpResponse.body());

      JsonNodeWrapper jsonNodeWrapper = jsonConverter.readTree(body);

      String accessToken = jsonNodeWrapper.getValueOrEmptyAsString("access_token");

      return new AccessTokenEntity(accessToken);
    } catch (IOException | InterruptedException | URISyntaxException e) {
      log.severe(e.getMessage());
      throw new HttpNetworkErrorException("unexpected network error", e);
    }
  }
}
