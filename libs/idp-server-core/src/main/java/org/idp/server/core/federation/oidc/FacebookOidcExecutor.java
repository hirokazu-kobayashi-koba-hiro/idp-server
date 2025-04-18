package org.idp.server.core.federation.oidc;

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
import org.idp.server.core.federation.SsoProvider;

public class FacebookOidcExecutor implements OidcSsoExecutor {

  Logger log = Logger.getLogger(FacebookOidcExecutor.class.getName());
  HttpClient httpClient;
  JsonConverter jsonConverter;

  public FacebookOidcExecutor() {
    this.httpClient = HttpClientFactory.defaultClient();
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  @Override
  public SsoProvider type() {
    return SupportedOidcProvider.Facebook.toSsoProvider();
  }

  @Override
  public OidcTokenResponse requestToken(OidcTokenRequest oidcTokenRequest) {
    try {

      QueryParams queryParams = new QueryParams(oidcTokenRequest.toMap());

      HttpRequest.Builder builder =
          HttpRequest.newBuilder()
              .uri(new URI(oidcTokenRequest.endpoint()))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .header("Accept", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(queryParams.params()));

      if (oidcTokenRequest.isClientSecretBasic()) {
        builder.header("Authorization", oidcTokenRequest.basicAuthenticationValue());
      }

      HttpRequest request = builder.build();

      HttpResponse<String> httpResponse =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      String body = httpResponse.body();

      validateResponse(httpResponse, body);

      Map map = jsonConverter.read(body, Map.class);

      return new OidcTokenResponse(map);
    } catch (IOException | InterruptedException | URISyntaxException e) {
      log.severe(e.getMessage());
      throw new HttpNetworkErrorException("unexpected network error", e);
    }
  }

  @Override
  public OidcJwksResponse getJwks(OidcJwksRequest oidcJwksRequest) {

    try {

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(new URI(oidcJwksRequest.endpoint()))
              .header("Content-Type", "application/json")
              .header("Accept", "application/json")
              .GET()
              .build();

      HttpResponse<String> httpResponse =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      String body = httpResponse.body();
      log.info("jwks response:" + body);

      validateResponse(httpResponse, body);

      return new OidcJwksResponse(body);
    } catch (IOException | InterruptedException | URISyntaxException e) {
      log.severe(e.getMessage());
      throw new HttpNetworkErrorException("unexpected network error", e);
    }
  }

  @Override
  public OidcUserinfoResponse requestUserInfo(OidcUserinfoRequest oidcUserinfoRequest) {
    try {

      QueryParams queryParams = new QueryParams();
      queryParams.add("fields", "id,name,email,picture");
      queryParams.add("access_token", oidcUserinfoRequest.accessToken());
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(new URI(oidcUserinfoRequest.endpoint() + "?" + queryParams.params()))
              .header("Content-Type", "application/json")
              .header("Accept", "application/json")
              .GET()
              .build();

      HttpResponse<String> httpResponse =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      String body = httpResponse.body();

      validateResponse(httpResponse, body);

      Map map = jsonConverter.read(body, Map.class);

      return new OidcUserinfoResponse(map);
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
