package org.idp.server.notification.push.fcm;

import org.idp.server.core.authentication.device.AuthenticationDeviceNotifier;
import org.idp.server.core.notification.push.PushNotificationChannel;
import org.idp.server.core.oauth.identity.device.AuthenticationDevice;

import java.util.logging.Logger;

public class FcmNotifier implements AuthenticationDeviceNotifier {

    Logger log = Logger.getLogger(FcmNotifier.class.getName());

    @Override
    public PushNotificationChannel chanel() {
        return new PushNotificationChannel("fcm");
    }

    @Override
    public void notify(AuthenticationDevice device) {
        log.info("Fcm notification channel called");
    }
}
