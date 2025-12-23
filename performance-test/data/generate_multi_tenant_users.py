#!/usr/bin/env python3
"""
Multi-Tenant User Data Generator for Scalability Tests

This script generates user and authentication device data for multiple tenants.
It reads tenant information from performance-test-tenant.json and generates
TSV files that can be imported into PostgreSQL.

Usage:
    python3 generate_multi_tenant_users.py [--users-per-tenant N] [--tenants-file FILE]

Options:
    --users-per-tenant N    Number of users per tenant (default: 100000)
    --tenants-file FILE     Path to tenant JSON file (default: performance-test-tenant.json)
"""

import uuid
import os
import json
import argparse
from pathlib import Path

# Default configuration
DEFAULT_USERS_PER_TENANT = 100000
DEFAULT_TENANTS_FILE = "./performance-test/data/performance-test-tenant.json"
OUTPUT_DIR = "./performance-test/data"
PROVIDER_ID = "idp-server"
EMAIL_DOMAIN = "example.com"

# Output files
OUTPUT_FILE_USERS = os.path.join(OUTPUT_DIR, "generated_multi_tenant_users.tsv")
OUTPUT_FILE_DEVICES = os.path.join(OUTPUT_DIR, "generated_multi_tenant_devices.tsv")
OUTPUT_FILE_TEST_USERS = os.path.join(OUTPUT_DIR, "performance-test-multi-tenant-users.json")


def load_tenants(tenants_file: str) -> list:
    """Load tenant information from JSON file."""
    if not os.path.exists(tenants_file):
        print(f"❌ Tenant file not found: {tenants_file}")
        print("   Please run register-tenants.sh first to create tenants.")
        exit(1)

    with open(tenants_file, 'r') as f:
        tenants = json.load(f)

    print(f"📋 Loaded {len(tenants)} tenants from {tenants_file}")
    return tenants


def generate_users_for_tenants(tenants: list, users_per_tenant: int):
    """Generate user and device data for all tenants."""

    os.makedirs(OUTPUT_DIR, exist_ok=True)

    total_users = len(tenants) * users_per_tenant
    print(f"🚀 Generating {total_users:,} users ({users_per_tenant:,} per tenant × {len(tenants)} tenants)")

    # Structure to hold test users for each tenant (first 500 per tenant)
    test_users_per_tenant = {tenant['tenantId']: [] for tenant in tenants}

    with open(OUTPUT_FILE_USERS, "w") as f_users, \
         open(OUTPUT_FILE_DEVICES, "w") as f_devices:

        user_count = 0

        for tenant_idx, tenant in enumerate(tenants):
            tenant_id = tenant['tenantId']
            print(f"\n📦 Tenant {tenant_idx + 1}/{len(tenants)}: {tenant_id}")

            for i in range(1, users_per_tenant + 1):
                user_sub = str(uuid.uuid4())
                device_id = str(uuid.uuid4())
                user_id = f"user_{uuid.uuid4()}"
                email = f"{user_id}@{EMAIL_DOMAIN}"
                phone = f"090{str(tenant_idx)}{str(i).zfill(7)}"

                # idp_user table data
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

                user_line = (
                    f"{user_sub}\t{tenant_id}\t{PROVIDER_ID}\t{user_id}\t{user_id}\t"
                    f"{email}\ttrue\t{phone}\ttrue\t{email}\tIDENTITY_VERIFIED\t{devices_escaped}\n"
                )
                f_users.write(user_line)

                # idp_user_authentication_devices table data
                device_line = (
                    f"{device_id}\t{tenant_id}\t{user_sub}\tiOS 18.5\tiPhone15\tiOS\t\t"
                    f"Test App\t1\t[]\ttest token\tfcm\n"
                )
                f_devices.write(device_line)

                # Collect first 500 users per tenant for test data
                if i <= 500:
                    test_users_per_tenant[tenant_id].append({
                        "device_id": device_id,
                        "user_id": user_sub,
                        "email": email,
                        "phone": phone,
                        "external_user_id": user_id,
                        "provider_id": PROVIDER_ID
                    })

                user_count += 1
                if user_count % 100000 == 0:
                    print(f"   Progress: {user_count:,} / {total_users:,}")

    print(f"\n✅ Users TSV complete: {OUTPUT_FILE_USERS}")
    print(f"✅ Devices TSV complete: {OUTPUT_FILE_DEVICES}")

    # Generate test users JSON
    generate_test_users_json(tenants, test_users_per_tenant)

    return user_count


def generate_test_users_json(tenants: list, test_users_per_tenant: dict):
    """Generate JSON file with test users for k6 scripts."""

    output_data = []

    for tenant in tenants:
        tenant_id = tenant['tenantId']
        client_id = tenant['clientId']
        client_secret = tenant.get('clientSecret', 'clientSecretPostPassword1234567890123456789012345678901234567890123456789012345678901234567890')

        output_data.append({
            "tenantId": tenant_id,
            "clientId": client_id,
            "clientSecret": client_secret,
            "users": test_users_per_tenant[tenant_id]
        })

    with open(OUTPUT_FILE_TEST_USERS, 'w') as f:
        json.dump(output_data, f, indent=2)

    print(f"✅ Test users JSON complete: {OUTPUT_FILE_TEST_USERS}")


def main():
    parser = argparse.ArgumentParser(
        description='Generate multi-tenant user data for scalability tests'
    )
    parser.add_argument(
        '--users-per-tenant',
        type=int,
        default=DEFAULT_USERS_PER_TENANT,
        help=f'Number of users per tenant (default: {DEFAULT_USERS_PER_TENANT})'
    )
    parser.add_argument(
        '--tenants-file',
        type=str,
        default=DEFAULT_TENANTS_FILE,
        help=f'Path to tenant JSON file (default: {DEFAULT_TENANTS_FILE})'
    )

    args = parser.parse_args()

    print("=" * 60)
    print("Multi-Tenant User Data Generator")
    print("=" * 60)

    # Load tenants
    tenants = load_tenants(args.tenants_file)

    if len(tenants) == 0:
        print("❌ No tenants found in the file.")
        exit(1)

    # Generate users
    total = generate_users_for_tenants(tenants, args.users_per_tenant)

    print("\n" + "=" * 60)
    print(f"✅ Generation complete!")
    print(f"   Total users: {total:,}")
    print(f"   Total devices: {total:,}")
    print("=" * 60)

    print("\n📝 Next steps:")
    print("   1. Import users to PostgreSQL:")
    print(f"      psql ... -c \"\\COPY idp_user (...) FROM '{OUTPUT_FILE_USERS}' ...\"")
    print("   2. Import devices to PostgreSQL:")
    print(f"      psql ... -c \"\\COPY idp_user_authentication_devices (...) FROM '{OUTPUT_FILE_DEVICES}' ...\"")


if __name__ == "__main__":
    main()
