package org.idp.server.core.security.hook.ssf;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.Logger;
import org.idp.server.core.basic.http.HttpClientErrorException;
import org.idp.server.core.basic.http.HttpClientFactory;
import org.idp.server.core.basic.http.HttpNetworkErrorException;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.security.SecurityEvent;
import org.idp.server.core.security.SecurityEventHookExecutor;
import org.idp.server.core.security.hook.*;
import org.idp.server.core.tenant.Tenant;

public class SsfHookExecutor implements SecurityEventHookExecutor {

  Logger log = Logger.getLogger(SsfHookExecutor.class.getName());
  HttpClient httpClient;
  JsonConverter jsonConverter;

  public SsfHookExecutor() {
    this.httpClient = HttpClientFactory.defaultClient();
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  @Override
  public SecurityEventHookType type() {
    return StandardSecurityEventHookType.SSF.toHookType();
  }

  @Override
  public SecurityEventHookResult execute(
      Tenant tenant,
      SecurityEvent securityEvent,
      SecurityEventHookConfiguration hookConfiguration) {

    SecurityEventTokenEntityConvertor convertor =
        new SecurityEventTokenEntityConvertor(securityEvent);
    SecurityEventTokenEntity securityEventTokenEntity = convertor.convert();

    SharedSignalFrameworkConfiguration ssfConfiguration =
        jsonConverter.read(hookConfiguration.details(), SharedSignalFrameworkConfiguration.class);

    log.info(
        String.format(
            "notify shared signal (%s) to (%s)",
            securityEventTokenEntity.securityEventAsString(), ssfConfiguration.issuer()));
    SecurityEventTokenCreator securityEventTokenCreator =
        new SecurityEventTokenCreator(
            securityEventTokenEntity, ssfConfiguration.privateKey(securityEvent.type()));
    SecurityEventToken securityEventToken = securityEventTokenCreator.create();

    send(
        new SharedSignalEventRequest(
            ssfConfiguration.endpoint(securityEvent.type()),
            ssfConfiguration.headers(securityEvent.type()),
            securityEventToken));

    return new SecurityEventHookResult();
  }

  private void send(SharedSignalEventRequest sharedSignalEventRequest) {
    try {

      HttpRequest.Builder builder =
          HttpRequest.newBuilder()
              .uri(new URI(sharedSignalEventRequest.endpoint()))
              .header("Content-Type", "application/secevent+jwt")
              .header("Accept", "application/json")
              .POST(
                  HttpRequest.BodyPublishers.ofString(
                      sharedSignalEventRequest.securityEventTokenValue()));

      HttpRequest request = builder.build();

      HttpResponse<String> httpResponse =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      validateResponse(httpResponse);

    } catch (IOException | InterruptedException | URISyntaxException e) {
      log.severe(e.getMessage());
      throw new HttpNetworkErrorException("unexpected network error", e);
    }
  }

  private void validateResponse(HttpResponse<String> httpResponse) {
    String body = httpResponse.body();
    if (httpResponse.statusCode() >= 400 && httpResponse.statusCode() < 500) {

      log.warning("ssf response:" + body);
      throw new HttpClientErrorException(body, httpResponse.statusCode());
    }

    if (httpResponse.statusCode() >= 500) {
      log.severe("ssf response:" + body);
      throw new HttpClientErrorException(body, httpResponse.statusCode());
    }
  }
}
