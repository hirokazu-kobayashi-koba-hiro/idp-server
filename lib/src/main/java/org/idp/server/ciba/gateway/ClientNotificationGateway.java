package org.idp.server.ciba.gateway;

import org.idp.server.ciba.clientnotification.ClientNotificationRequest;

public interface ClientNotificationGateway {

  void notify(ClientNotificationRequest clientNotificationRequest);
}
