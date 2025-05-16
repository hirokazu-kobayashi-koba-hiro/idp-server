package org.idp.server.basic.type.oauth;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

/** RequestUri */
public class RequestUri {
  String value;
  private static final String PUSHED_REQUEST_URI_PREFIX = "urn:ietf:params:oauth:request_uri:";

  public RequestUri() {}

  public RequestUri(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }

  public static String createPushedRequestUri(String id) {
    return PUSHED_REQUEST_URI_PREFIX + id;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RequestUri that = (RequestUri) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  public URI toURI() throws URISyntaxException {
    return new URI(value);
  }

  public boolean isPushedRequestUri() {
    return value.startsWith(PUSHED_REQUEST_URI_PREFIX);
  }

  public String extractId() {
    return value.substring(PUSHED_REQUEST_URI_PREFIX.length());
  }
}
