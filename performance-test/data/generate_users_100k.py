import uuid
import os
import json

OUTPUT_FILE = "./performance-test/data/generated_users_100k.tsv"
TENANT_ID = "67e7eae6-62b0-4500-9eff-87459f63fc66"
PROVIDER_ID = "idp-server"
EMAIL_DOMAIN = "example.com"

os.makedirs(os.path.dirname(OUTPUT_FILE), exist_ok=True)

with open(OUTPUT_FILE, "w") as f:
    for i in range(1, 100_001):
        user_sub = str(uuid.uuid4())
        user_id = f"user_{uuid.uuid4()}"
        email = f"{user_id}@{EMAIL_DOMAIN}"
        phone = f"090{str(i).zfill(8)}"
        devices = [{
            "id": user_sub,
            "os": "iOS 18.5",
            "model": "iPhone15",
            "platform": "iOS",
            "notification_token": "test token",
            "notification_channel": "fcm",
            "preferred_for_notification": True
        }]
        devices_str = json.dumps(devices, ensure_ascii=False)
        # " to "" ""
        devices_escaped = '"' + devices_str.replace('"', '""') + '"'
        line = f"{user_sub}\t{TENANT_ID}\t{PROVIDER_ID}\t{user_id}\t{user_id}\t{email}\ttrue\t{phone}\ttrue\t{email}\tIDENTITY_VERIFIED\t{devices_escaped}\n"
        f.write(line)
        if i % 1000 == 0:
            print(f"progress: {i}")
print(f"✅ TSV complete：{OUTPUT_FILE}")