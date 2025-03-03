package org.idp.server.core.basic.http;

import java.net.URI;
import java.util.Objects;

public class UriWrapper {
  URI value;

  public UriWrapper(URI value) {
    this.value = value;
  }

  public UriWrapper(String value) throws InvalidUriException {
    try {
      this.value = new URI(value);
    } catch (Exception exception) {
      throw new InvalidUriException(exception);
    }
  }

  public int getPort() {
    int port = value.getPort();
    if (port != -1) {
      return port;
    }
    if (value.getScheme().equals("https")) {
      return 443;
    }
    if (value.getScheme().equals("http")) {
      return 80;
    }
    return -1;
  }

  public boolean equalsUserinfo(UriWrapper other) {
    if (!hasUserinfo() && !other.hasUserinfo()) {
      return true;
    }
    if (hasUserinfo() && !other.hasUserinfo()) {
      return false;
    }
    if (!hasUserinfo() && other.hasUserinfo()) {
      return false;
    }
    return getUserinfo().equals(other.getUserinfo());
  }

  public boolean equalsPath(UriWrapper other) {
    if (!hasPath() && !other.hasPath()) {
      return true;
    }
    if (hasPath() && !other.hasPath()) {
      return false;
    }
    if (!hasPath() && other.hasPath()) {
      return false;
    }
    return getPath().equals(other.getPath());
  }

  public boolean equalsHost(UriWrapper other) {
    return getHost().equalsIgnoreCase(other.getHost());
  }

  public boolean equalsPort(UriWrapper other) {
    return getPort() == other.getPort();
  }

  public String getHost() {
    return value.getHost();
  }

  public String getUserinfo() {
    return value.getUserInfo();
  }

  public boolean hasUserinfo() {
    return Objects.nonNull(value.getUserInfo());
  }

  public String getPath() {
    return value.getPath();
  }

  public boolean hasPath() {
    return Objects.nonNull(value.getPath());
  }

  public boolean hasFragment() {
    return Objects.nonNull(value.getFragment());
  }
}
