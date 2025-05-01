package org.idp.server.notification.push.fcm;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.idp.server.core.authentication.device.AuthenticationDeviceNotificationConfiguration;
import org.idp.server.core.authentication.device.AuthenticationDeviceNotifier;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.authentication.notification.device.NotificationChannel;
import org.idp.server.core.authentication.notification.device.NotificationTemplate;
import org.idp.server.core.identity.device.AuthenticationDevice;
import org.idp.server.core.tenant.Tenant;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class FcmNotifier implements AuthenticationDeviceNotifier {

    Logger log = Logger.getLogger(FcmNotifier.class.getName());
    JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
    Map<String, FirebaseMessaging> cache = new ConcurrentHashMap<>();

    @Override
    public NotificationChannel chanel() {
        return new NotificationChannel("fcm");
    }

    @Override
    public void notify(Tenant tenant, AuthenticationDevice device, AuthenticationDeviceNotificationConfiguration configuration) {

        try {
            log.info("Fcm notification channel called");

            FcmConfiguration fcmConfiguration =  jsonConverter.read(configuration.getDetail(chanel()), FcmConfiguration.class);
            FirebaseMessaging firebaseMessaging = getOrInitFirebaseMessaging(tenant, fcmConfiguration);

            NotificationTemplate notificationTemplate = fcmConfiguration.findTemplate("default");

            Notification notification = Notification.builder()
                    .setTitle(notificationTemplate.subject())
                    .setBody(notificationTemplate.body())
                    .build();

            Message firebaseMessage = Message.builder()
                    .setToken(device.notificationToken().value())
                    .setNotification(notification)
                    .build();

            String result = firebaseMessaging.send(firebaseMessage);

            log.info("fcm result: " + result);
        } catch (Exception e) {

            log.severe("Fcm is failed: " + e.getMessage());
        }
    }

    FirebaseMessaging getOrInitFirebaseMessaging(Tenant tenant, FcmConfiguration fcmConfiguration) throws IOException {

        if (cache.containsKey(tenant.identifierValue())) {
            return cache.get(tenant.identifierValue());
        }

        String credential = fcmConfiguration.credential();
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(new ByteArrayInputStream(credential.getBytes())))
                .build();

        FirebaseApp firebaseApp = FirebaseApp.initializeApp(options, tenant.identifierValue());
        FirebaseMessaging firebaseMessaging = FirebaseMessaging.getInstance(firebaseApp);
        cache.put(tenant.identifierValue(), firebaseMessaging);
        return firebaseMessaging;
    }
}
