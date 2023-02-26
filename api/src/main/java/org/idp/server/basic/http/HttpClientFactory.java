package org.idp.server.basic.http;

import java.net.Authenticator;
import java.net.http.HttpClient;
import java.time.Duration;

public class HttpClientFactory {
  public static HttpClient defaultClient() {
    return HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(20))
        .authenticator(Authenticator.getDefault())
        .build();
  }
}
