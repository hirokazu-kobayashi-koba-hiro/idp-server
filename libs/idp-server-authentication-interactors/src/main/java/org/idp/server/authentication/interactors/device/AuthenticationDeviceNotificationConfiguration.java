package org.idp.server.authentication.interactors.device;

import java.util.Map;
import org.idp.server.core.oidc.identity.device.NotificationChannel;
import org.idp.server.authentication.interactors.webauthn.WebAuthnCredentialNotFoundException;

public class AuthenticationDeviceNotificationConfiguration {
  String channel;
  Map<String, Map<String, Object>> details;

  public AuthenticationDeviceNotificationConfiguration() {}

  public AuthenticationDeviceNotificationConfiguration(
      String channel, Map<String, Map<String, Object>> details) {
    this.channel = channel;
    this.details = details;
  }

  public NotificationChannel chanel() {
    return new NotificationChannel(channel);
  }

  public Map<String, Map<String, Object>> details() {
    return details;
  }

  public Map<String, Object> getDetail(NotificationChannel channel) {

    if (!details.containsKey(channel.name())) {
      throw new WebAuthnCredentialNotFoundException(
          "invalid configuration. key: " + channel.name() + " is unregistered.");
    }

    return details.get(channel.name());
  }
}
