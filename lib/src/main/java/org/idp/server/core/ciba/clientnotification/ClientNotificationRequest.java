package org.idp.server.core.ciba.clientnotification;

public record ClientNotificationRequest(String endpoint, String body, String token) {}
