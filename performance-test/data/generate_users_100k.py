import uuid
import os
import json

OUTPUT_FILE_USERS = "./performance-test/data/generated_users_100k.tsv"
OUTPUT_FILE_DEVICES = "./performance-test/data/generated_user_devices_100k.tsv"
TENANT_ID = "67e7eae6-62b0-4500-9eff-87459f63fc66"
PROVIDER_ID = "idp-server"
EMAIL_DOMAIN = "example.com"

os.makedirs(os.path.dirname(OUTPUT_FILE_USERS), exist_ok=True)

with open(OUTPUT_FILE_USERS, "w") as f_users, open(OUTPUT_FILE_DEVICES, "w") as f_devices:
    for i in range(1, 1_000_001):
        user_sub = str(uuid.uuid4())
        device_id = str(uuid.uuid4())
        user_id = f"user_{uuid.uuid4()}"
        email = f"{user_id}@{EMAIL_DOMAIN}"
        phone = f"090{str(i).zfill(8)}"

        # idp_user テーブル用（authentication_devices は後方互換性のため残す）
        devices = [{
            "id": device_id,
            "os": "iOS 18.5",
            "model": "iPhone15",
            "platform": "iOS",
            "priority": 1,
            "notification_token": "test token",
            "notification_channel": "fcm",
            "preferred_for_notification": True
        }]
        devices_str = json.dumps(devices, ensure_ascii=False)
        devices_escaped = '"' + devices_str.replace('"', '""') + '"'
        user_line = f"{user_sub}\t{TENANT_ID}\t{PROVIDER_ID}\t{user_id}\t{user_id}\t{email}\ttrue\t{phone}\ttrue\t{email}\tIDENTITY_VERIFIED\t{devices_escaped}\n"
        f_users.write(user_line)

        # idp_user_authentication_devices テーブル用
        device_line = f"{device_id}\t{TENANT_ID}\t{user_sub}\tiOS 18.5\tiPhone15\tiOS\t\tTest App\t1\t[]\ttest token\tfcm\n"
        f_devices.write(device_line)

        if i % 1000 == 0:
            print(f"progress: {i}")

print(f"✅ Users TSV complete: {OUTPUT_FILE_USERS}")
print(f"✅ Devices TSV complete: {OUTPUT_FILE_DEVICES}")
