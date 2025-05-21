package org.idp.server.core.extension.ciba.gateway;

import org.idp.server.core.extension.ciba.clientnotification.ClientNotificationRequest;

public interface ClientNotificationGateway {

  void notify(ClientNotificationRequest clientNotificationRequest);
}
