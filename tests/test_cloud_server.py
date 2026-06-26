import json
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path
from unittest.mock import patch

from haoleme.cloud_server import (
    active_user_stats,
    AuthContext,
    append_run_update,
    authenticate_device_token,
    build_run_fetch_payload,
    account_has_cloud_data,
    backup_database,
    cancel_pair,
    can_read_run,
    connect,
    consume_space_join_code,
    confirm_pair,
    create_space_join_code,
    create_pair,
    delete_account,
    delete_all_runs,
    expire_stale_running_runs,
    get_pair,
    get_run,
    init_db,
    health_payload,
    find_app_token,
    get_space_join_code,
    is_e2ee_run,
    is_app_version_too_old,
    legacy_admin_token_allowed,
    iso_now,
    delete_run,
    delete_runs_for_device,
    list_devices,
    list_pending_interrupts,
    list_runs,
    latest_backup_status,
    monitor_payload,
    permission_audit,
    rename_device,
    record_device_heartbeat,
    sanitize_gpus,
    request_run_interrupt,
    revoke_device,
    store_device_token,
    store_app_token,
    sync_space_id,
    server_allows_legacy_admin_tokens,
    server_allows_existing_legacy_accounts,
    token_hash,
    upsert_device,
    upsert_run,
)


class CloudServerDeviceTest(unittest.TestCase):
    def test_renamed_device_is_not_overwritten_by_later_sync(self):
        with tempfile.TemporaryDirectory() as tmp:
            db_path = Path(tmp) / "cloud.db"
            account_key = "account-key"
            device_id = "dev_123"
            init_db(db_path)

            upsert_device(db_path, account_key, device_id, "Old name", "2026-06-18T01:00:00Z")
            upsert_run(db_path, account_key, self.sample_run("run-1", device_id, "Old name"))

            renamed = rename_device(db_path, account_key, device_id, "Server A")

            self.assertIsNotNone(renamed)
            self.assertEqual(renamed["name"], "Server A")
            self.assertEqual(list_devices(db_path, account_key)[0]["name"], "Server A")
            self.assertEqual(list_runs(db_path, account_key, 10)[0]["deviceName"], "Server A")

            upsert_device(db_path, account_key, device_id, "Old name", "2026-06-18T01:05:00Z")
            upsert_run(db_path, account_key, self.sample_run("run-2", device_id, "Old name"))

            devices = list_devices(db_path, account_key)
            runs = list_runs(db_path, account_key, 10)
            self.assertEqual(devices[0]["name"], "Server A")
            self.assertEqual([run["deviceName"] for run in runs], ["Server A", "Server A"])

    def test_device_heartbeat_updates_last_seen_without_renaming_manual_device(self):
        with tempfile.TemporaryDirectory() as tmp:
            db_path = Path(tmp) / "cloud.db"
            account_key = "account-key"
            device_id = "dev_123"
            init_db(db_path)

            upsert_device(db_path, account_key, device_id, "Old name", "2026-06-18T01:00:00Z")
            rename_device(db_path, account_key, device_id, "Server A")

            device = record_device_heartbeat(db_path, account_key, device_id, "Auto name", "2026-06-18T01:05:00Z")

            self.assertIsNotNone(device)
            self.assertEqual(device["name"], "Server A")
            self.assertEqual(device["lastSeenAt"], "2026-06-18T01:05:00Z")

    def test_active_user_stats_counts_distinct_accounts_by_recency(self):
        with tempfile.TemporaryDirectory() as tmp:
            db_path = Path(tmp) / "cloud.db"
            init_db(db_path)
            now = datetime.now(timezone.utc)

            def iso(delta):
                return (now - delta).isoformat().replace("+00:00", "Z")

            # (account, last_used_at age, revoked)
            tokens = [
                ("acc1", timedelta(minutes=1), False),   # online + dau + mau
                ("acc1", timedelta(minutes=2), False),   # same account, 2nd install
                ("acc2", timedelta(hours=5), False),     # dau + mau
                ("acc3", timedelta(days=10), False),     # mau only
                ("acc4", timedelta(days=60), False),     # outside all windows (still total)
                ("acc5", timedelta(minutes=1), True),    # revoked -> ignored
            ]
            with connect(db_path) as db:
                for i, (acc, age, revoked) in enumerate(tokens):
                    db.execute(
                        "INSERT INTO app_tokens(token_hash, account_key, client_id, client_name,"
                        " platform, created_at, last_used_at, revoked_at) VALUES (?,?,?,?,?,?,?,?)",
                        (f"hash{i}", acc, f"app_{i}", "n", "android",
                         iso(timedelta(days=90)), iso(age), iso(timedelta(0)) if revoked else ""),
                    )

            stats = active_user_stats(db_path)
            self.assertEqual(stats["appOnline"], 1)          # acc1
            self.assertEqual(stats["appDau"], 2)             # acc1, acc2
            self.assertEqual(stats["appMau"], 3)             # acc1, acc2, acc3
            self.assertEqual(stats["appTotalAccounts"], 4)   # acc1..acc4 (revoked excluded)
            self.assertEqual(stats["appInstalls"], 5)        # non-revoked tokens

    def test_device_heartbeat_stores_and_returns_gpus(self):
        with tempfile.TemporaryDirectory() as tmp:
            db_path = Path(tmp) / "cloud.db"
            account_key = "account-key"
            device_id = "dev_gpu"
            init_db(db_path)

            gpus = sanitize_gpus([
                {"index": "0", "name": "NVIDIA A100", "utilization": "37",
                 "memoryUsed": "1024", "memoryTotal": "81920", "temperature": "45"},
                "junk",
            ])
            device = record_device_heartbeat(
                db_path, account_key, device_id, "GPU box", "2026-06-23T01:00:00Z", gpus=gpus
            )
            self.assertEqual(len(device["gpus"]), 1)
            self.assertEqual(device["gpus"][0]["utilization"], 37)
            self.assertEqual(device["gpus"][0]["name"], "NVIDIA A100")

            listed = list_devices(db_path, account_key)
            self.assertEqual(listed[0]["gpus"][0]["memoryTotal"], 81920)

            # A heartbeat without gpus must not wipe the last known values.
            record_device_heartbeat(db_path, account_key, device_id, "GPU box", "2026-06-23T01:01:00Z")
            self.assertEqual(list_devices(db_path, account_key)[0]["gpus"][0]["utilization"], 37)

    def test_device_token_is_write_scoped_hashed_and_revocable(self):
        with tempfile.TemporaryDirectory() as tmp:
            db_path = Path(tmp) / "cloud.db"
            account_key = "account-key"
            device_id = "dev_123"
            token = "device-token-secret"
            init_db(db_path)

            upsert_device(db_path, account_key, device_id, "Server A", "2026-06-18T01:00:00Z")
            store_device_token(db_path, account_key, device_id, "Server A", token, "2026-06-18T01:00:00Z")

            authenticated = authenticate_device_token(db_path, token_hash(token))

            self.assertIsNotNone(authenticated)
            self.assertEqual(authenticated["account_key"], account_key)
            self.assertEqual(authenticated["scope"], "write")
            self.assertEqual(authenticated["device_id"], device_id)
            self.assertEqual(len(list_devices(db_path, account_key)), 1)

            self.assertTrue(revoke_device(db_path, account_key, device_id))
            self.assertIsNone(authenticate_device_token(db_path, token_hash(token)))
            self.assertEqual(list_devices(db_path, account_key), [])

    def test_revoked_device_reappears_when_paired_again(self):
        with tempfile.TemporaryDirectory() as tmp:
            db_path = Path(tmp) / "cloud.db"
            account_key = "account-key"
            device_id = "dev_123"
            init_db(db_path)

            upsert_device(db_path, account_key, device_id, "Server A", "2026-06-18T01:00:00Z")
            self.assertTrue(revoke_device(db_path, account_key, device_id))
            self.assertEqual(list_devices(db_path, account_key), [])

            upsert_device(db_path, account_key, device_id, "Server A", "2026-06-18T01:05:00Z")

            devices = list_devices(db_path, account_key)
            self.assertEqual(len(devices), 1)
            self.assertEqual(devices[0]["id"], device_id)
            self.assertEqual(devices[0]["revokedAt"], "")

    def test_pending_pair_can_be_cancelled_only_with_pair_token(self):
        with tempfile.TemporaryDirectory() as tmp:
            db_path = Path(tmp) / "cloud.db"
            init_db(db_path)

            self.assertTrue(create_pair(db_path, "123456", "pair-secret", "dev_123", "Server A", 1.0))
            self.assertEqual(get_pair(db_path, "123456")["pair_token"], token_hash("pair-secret"))
            self.assertFalse(cancel_pair(db_path, "123456", "wrong-secret", "2026-06-18T01:00:00Z"))
            self.assertEqual(get_pair(db_path, "123456")["status"], "pending")

            self.assertTrue(cancel_pair(db_path, "123456", "pair-secret", "2026-06-18T01:00:01Z"))
            self.assertEqual(get_pair(db_path, "123456")["status"], "cancelled")
            self.assertFalse(cancel_pair(db_path, "123456", "pair-secret", "2026-06-18T01:00:02Z"))

    def test_runs_can_be_filtered_by_status(self):
        with tempfile.TemporaryDirectory() as tmp:
            db_path = Path(tmp) / "cloud.db"
            account_key = "account-key"
            device_id = "dev_123"
            init_db(db_path)

            upsert_run(db_path, account_key, self.sample_run("run-1", device_id, "Server A", "running"))
            upsert_run(db_path, account_key, self.sample_run("run-2", device_id, "Server A", "failed"))
            upsert_run(db_path, account_key, self.sample_run("run-3", device_id, "Server A", "succeeded"))

            self.assertEqual([run["id"] for run in list_runs(db_path, account_key, 10, "", "running")], ["run-1"])
            self.assertEqual([run["id"] for run in list_runs(db_path, account_key, 10, "", "failed")], ["run-2"])
            self.assertEqual([run["id"] for run in list_runs(db_path, account_key, 10, "", "succeeded")], ["run-3"])

    def test_runs_can_be_deleted_for_one_device(self):
        with tempfile.TemporaryDirectory() as tmp:
            db_path = Path(tmp) / "cloud.db"
            account_key = "account-key"
            init_db(db_path)

            upsert_device(db_path, account_key, "dev_a", "Server A", "2026-06-18T01:00:00Z")
            upsert_device(db_path, account_key, "dev_b", "Server B", "2026-06-18T01:00:00Z")
            upsert_run(db_path, account_key, self.sample_run("run-1", "dev_a", "Server A"))
            upsert_run(db_path, account_key, self.sample_run("run-2", "dev_a", "Server A"))
            upsert_run(db_path, account_key, self.sample_run("run-3", "dev_b", "Server B"))

            self.assertEqual(delete_runs_for_device(db_path, account_key, "dev_a"), 2)

            self.assertEqual([run["id"] for run in list_runs(db_path, account_key, 10)], ["run-3"])
            self.assertEqual([device["id"] for device in list_devices(db_path, account_key)], ["dev_a", "dev_b"])

    def test_accounts_cannot_see_or_delete_each_other_runs(self):
        with tempfile.TemporaryDirectory() as tmp:
            db_path = Path(tmp) / "cloud.db"
            init_db(db_path)

            upsert_run(db_path, "account-a", self.sample_run("run-a", "dev_a", "A"))
            upsert_run(db_path, "account-b", self.sample_run("run-b", "dev_b", "B"))

            self.assertEqual([run["id"] for run in list_runs(db_path, "account-a", 10)], ["run-a"])
            self.assertEqual([run["id"] for run in list_runs(db_path, "account-b", 10)], ["run-b"])
            self.assertFalse(delete_runs_for_device(db_path, "account-a", "dev_b"))
            self.assertFalse(delete_run(db_path, "account-a", "run-b"))
            self.assertEqual([run["id"] for run in list_runs(db_path, "account-b", 10)], ["run-b"])

    def test_backup_health_and_permission_audit(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            db_path = root / "cloud.db"
            backup_dir = root / "backups"
            init_db(db_path)
            upsert_device(db_path, "account-a", "dev_a", "Server A", "2026-06-18T01:00:00Z")
            upsert_run(db_path, "account-a", self.sample_run("run-a", "dev_a", "Server A"))

            health = health_payload(db_path, 22)
            audit = permission_audit(db_path)
            backup_path = backup_database(db_path, backup_dir, keep=2)

            self.assertTrue(health["ok"])
            self.assertTrue(health["storage"]["ok"])
            self.assertGreaterEqual(health["storage"]["stats"]["runs"], 1)
            self.assertTrue(audit["ok"])
            self.assertTrue(backup_path.exists())
            self.assertGreater(backup_path.stat().st_size, 0)
            self.assertTrue(backup_path.with_suffix(backup_path.suffix + ".sha256").exists())
            self.assertTrue(latest_backup_status(backup_dir)["ok"])
            self.assertTrue(monitor_payload(db_path, backup_dir, 22, min_free_bytes=1)["ok"])

    def test_monitor_reports_missing_backup(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            db_path = root / "cloud.db"
            backup_dir = root / "backups"
            init_db(db_path)

            payload = monitor_payload(db_path, backup_dir, 22, min_free_bytes=1)

            self.assertFalse(payload["ok"])
            self.assertFalse(payload["checks"]["backup"])
            self.assertIn("no backups", payload["backup"]["error"])

    def test_sync_space_join_code_links_second_app_to_same_runs(self):
        with tempfile.TemporaryDirectory() as tmp:
            db_path = Path(tmp) / "cloud.db"
            init_db(db_path)
            owner_token = "owner-token-secret"
            account_key = token_hash(owner_token)
            upsert_run(db_path, account_key, self.sample_run("run-a", "dev_a", "A"))

            self.assertTrue(create_space_join_code(
                db_path,
                "839204",
                "share-secret",
                account_key,
                token_hash(owner_token),
                1.0,
                "encryption-key",
                "Owner phone",
            ))
            join = get_space_join_code(db_path, "839204")
            self.assertIsNotNone(join)
            self.assertEqual(join["account_key"], account_key)
            self.assertEqual(join["share_token"], token_hash("share-secret"))
            self.assertEqual(join["encryption_key"], "encryption-key")

            self.assertTrue(consume_space_join_code(db_path, "839204", "2026-06-18T01:00:00Z"))
            app_token = "second-app-token"
            store_app_token(db_path, account_key, "app_second", "Second phone", "android", app_token, "2026-06-18T01:00:00Z")
            app_auth = find_app_token(db_path, token_hash(app_token))

            self.assertIsNotNone(app_auth)
            self.assertEqual(app_auth["account_key"], account_key)
            self.assertEqual([run["id"] for run in list_runs(db_path, app_auth["account_key"], 10)], ["run-a"])
            self.assertEqual(sync_space_id(account_key)[:3], "sp_")
            self.assertFalse(consume_space_join_code(db_path, "839204", "2026-06-18T01:00:01Z"))

    def test_offline_device_does_not_cancel_running_run(self):
        # A stale device last_seen_at must NOT auto-cancel a running run: the
        # command may still be executing locally and survive a transient network
        # drop. The CLI heartbeat reconciles real status when it reconnects.
        with tempfile.TemporaryDirectory() as tmp:
            db_path = Path(tmp) / "cloud.db"
            account_key = "account-key"
            device_id = "dev_123"
            init_db(db_path)

            upsert_device(db_path, account_key, device_id, "Server A", "2026-06-18T01:00:00Z")
            upsert_run(db_path, account_key, self.sample_run("run-1", device_id, "Server A", "running"))

            with connect(db_path) as db:
                expired = expire_stale_running_runs(db, account_key, "2026-06-18T01:05:00Z")

            run = list_runs(db_path, account_key, 10)[0]
            self.assertEqual(expired, 0)
            self.assertEqual(run["status"], "running")

    def test_online_device_keeps_old_running_run_alive(self):
        with tempfile.TemporaryDirectory() as tmp:
            db_path = Path(tmp) / "cloud.db"
            account_key = "account-key"
            device_id = "dev_123"
            init_db(db_path)

            upsert_device(db_path, account_key, device_id, "Server A", iso_now())
            upsert_run(db_path, account_key, self.sample_run("run-1", device_id, "Server A", "running"))

            with connect(db_path) as db:
                expired = expire_stale_running_runs(db, account_key, "2026-06-18T01:05:00Z")

            run = list_runs(db_path, account_key, 10)[0]
            self.assertEqual(expired, 0)
            self.assertEqual(run["status"], "running")

    def test_pair_can_reuse_existing_device_id(self):
        with tempfile.TemporaryDirectory() as tmp:
            db_path = Path(tmp) / "cloud.db"
            account_key = "account-key"
            init_db(db_path)

            upsert_device(db_path, account_key, "dev_old", "Server A", "2026-06-18T01:00:00Z")
            self.assertTrue(create_pair(db_path, "123456", "pair-secret", "dev_new", "New login", 1.0))

            confirm_pair(
                db_path,
                "123456",
                "device-token",
                "dev_old",
                "Server A",
                32,
                "0.6.21",
                "android",
                "2026-06-18T01:05:00Z",
            )

            pair = get_pair(db_path, "123456")
            devices = list_devices(db_path, account_key)
            self.assertEqual(pair["device_id"], "dev_old")
            self.assertEqual(pair["device_name"], "Server A")
            self.assertEqual([device["id"] for device in devices], ["dev_old"])

    def test_same_name_devices_are_not_merged_without_exact_device_id(self):
        with tempfile.TemporaryDirectory() as tmp:
            db_path = Path(tmp) / "cloud.db"
            account_key = "account-key"
            init_db(db_path)

            upsert_device(db_path, account_key, "dev_first", "My Mac", "2026-06-18T01:00:00Z")
            upsert_device(db_path, account_key, "dev_second", "My Mac", "2026-06-18T02:00:00Z")

            self.assertTrue(create_pair(db_path, "654321", "pair-secret", "dev_third", "My Mac", 1.0))
            confirm_pair(
                db_path,
                "654321",
                "device-token",
                "dev_third",
                "My Mac",
                32,
                "0.6.21",
                "android",
                "2026-06-18T03:00:00Z",
            )

            pair = get_pair(db_path, "654321")
            self.assertEqual(pair["device_id"], "dev_third")

    def test_public_health_hides_storage_details(self):
        with tempfile.TemporaryDirectory() as tmp:
            db_path = Path(tmp) / "cloud.db"
            init_db(db_path)

            public = health_payload(db_path, 22, detailed=False)
            detailed = health_payload(db_path, 22, detailed=True)

            self.assertNotIn("storage", public)
            self.assertNotIn("disk", public)
            self.assertIn("security", public)
            self.assertIn("storage", detailed)

    def test_e2ee_run_detector_requires_ciphertext(self):
        self.assertFalse(is_e2ee_run(self.sample_run("run-plain", "dev_a", "A")))
        self.assertTrue(is_e2ee_run({
            **self.sample_run("run-e2ee", "dev_a", "A"),
            "e2ee": {
                "v": 1,
                "alg": "AES-256-GCM",
                "nonce": "abc",
                "ciphertext": "def",
            },
        }))

    def test_android_min_version_check_does_not_apply_to_ios(self):
        self.assertTrue(is_app_version_too_old("android", 1, 22))
        self.assertFalse(is_app_version_too_old("ios", 1, 22))
        self.assertFalse(is_app_version_too_old("ios", None, 22))

    def test_running_run_can_be_interrupted_and_flag_survives_cli_sync(self):
        with tempfile.TemporaryDirectory() as tmp:
            db_path = Path(tmp) / "cloud.db"
            account_key = "account-key"
            init_db(db_path)

            upsert_run(db_path, account_key, self.sample_run("run-1", "dev_123", "Server A", "running"))
            stored, error = request_run_interrupt(db_path, account_key, "run-1")

            self.assertIsNone(error)
            self.assertIsNotNone(stored)
            self.assertTrue(stored.get("interruptRequestedAt"))

            run = get_run(db_path, account_key, "run-1")
            self.assertTrue(run.get("interruptRequestedAt"))

            upsert_run(db_path, account_key, self.sample_run("run-1", "dev_123", "Server A", "running"))
            run = get_run(db_path, account_key, "run-1")
            self.assertTrue(run.get("interruptRequestedAt"))

    def test_pending_interrupts_lists_active_device_runs(self):
        with tempfile.TemporaryDirectory() as tmp:
            db_path = Path(tmp) / "cloud.db"
            account_key = "account-key"
            init_db(db_path)

            upsert_run(db_path, account_key, self.sample_run("run-1", "dev_123", "Server A", "running"))
            upsert_run(db_path, account_key, self.sample_run("run-2", "dev_other", "Other", "running"))
            request_run_interrupt(db_path, account_key, "run-1")

            interrupts = list_pending_interrupts(db_path, account_key, "dev_123")
            self.assertEqual([item["id"] for item in interrupts], ["run-1"])

    def test_write_token_can_read_own_run_for_interrupt_polling(self):
        admin = AuthContext(account_key="account-key", token_hash="admin", scope="admin")
        writer = AuthContext(account_key="account-key", token_hash="write", scope="write", device_id="dev_123")
        other = AuthContext(account_key="account-key", token_hash="write", scope="write", device_id="dev_other")
        run = self.sample_run("run-1", "dev_123", "Server A", "running")

        self.assertTrue(can_read_run(admin, run))
        self.assertTrue(can_read_run(writer, run))
        self.assertFalse(can_read_run(other, run))

    def test_interrupt_rejects_finished_run(self):
        with tempfile.TemporaryDirectory() as tmp:
            db_path = Path(tmp) / "cloud.db"
            account_key = "account-key"
            init_db(db_path)

            upsert_run(db_path, account_key, self.sample_run("run-1", "dev_123", "Server A", "succeeded"))
            stored, error = request_run_interrupt(db_path, account_key, "run-1")

            self.assertIsNone(stored)
            self.assertEqual(error, "run_not_active")

    def test_delete_all_runs_only_removes_current_account(self):
        with tempfile.TemporaryDirectory() as tmp:
            db_path = Path(tmp) / "cloud.db"
            init_db(db_path)

            upsert_run(db_path, "account-a", self.sample_run("run-a", "dev_a", "A"))
            upsert_run(db_path, "account-b", self.sample_run("run-b", "dev_b", "B"))

            deleted = delete_all_runs(db_path, "account-a")

            self.assertEqual(deleted, 1)
            self.assertEqual(list_runs(db_path, "account-a", 10), [])
            self.assertEqual([run["id"] for run in list_runs(db_path, "account-b", 10)], ["run-b"])

    def test_delete_account_removes_only_current_account_data(self):
        with tempfile.TemporaryDirectory() as tmp:
            db_path = Path(tmp) / "cloud.db"
            init_db(db_path)

            upsert_device(db_path, "account-a", "dev_a", "A", "2026-06-18T01:00:00Z")
            upsert_run(db_path, "account-a", self.sample_run("run-a", "dev_a", "A"))
            store_device_token(db_path, "account-a", "dev_a", "A", "device-token-a", "2026-06-18T01:00:00Z")
            store_app_token(db_path, "account-a", "app_a", "Phone A", "android", "app-token-a", "2026-06-18T01:00:00Z")
            create_space_join_code(db_path, "111111", "share-a", "account-a", "token-hash-a", 1.0)

            upsert_device(db_path, "account-b", "dev_b", "B", "2026-06-18T01:00:00Z")
            upsert_run(db_path, "account-b", self.sample_run("run-b", "dev_b", "B"))
            store_device_token(db_path, "account-b", "dev_b", "B", "device-token-b", "2026-06-18T01:00:00Z")
            store_app_token(db_path, "account-b", "app_b", "Phone B", "android", "app-token-b", "2026-06-18T01:00:00Z")
            create_space_join_code(db_path, "222222", "share-b", "account-b", "token-hash-b", 1.0)

            deleted = delete_account(db_path, "account-a")

            self.assertGreaterEqual(deleted, 5)
            self.assertEqual(list_runs(db_path, "account-a", 10), [])
            self.assertEqual(list_devices(db_path, "account-a"), [])
            self.assertIsNone(authenticate_device_token(db_path, token_hash("device-token-a")))
            self.assertIsNone(find_app_token(db_path, token_hash("app-token-a")))
            self.assertIsNone(get_space_join_code(db_path, "111111"))

            self.assertEqual([run["id"] for run in list_runs(db_path, "account-b", 10)], ["run-b"])
            self.assertEqual([device["id"] for device in list_devices(db_path, "account-b")], ["dev_b"])
            self.assertIsNotNone(authenticate_device_token(db_path, token_hash("device-token-b")))
            self.assertIsNotNone(find_app_token(db_path, token_hash("app-token-b")))
            self.assertIsNotNone(get_space_join_code(db_path, "222222"))

    def test_legacy_admin_tokens_default_to_disabled(self):
        with patch.dict("os.environ", {}, clear=True):
            self.assertFalse(server_allows_legacy_admin_tokens())

    def test_existing_legacy_accounts_are_allowed_without_opening_new_accounts(self):
        with tempfile.TemporaryDirectory() as tmp:
            db_path = Path(tmp) / "cloud.db"
            init_db(db_path)

            with patch.dict("os.environ", {}, clear=True):
                self.assertTrue(server_allows_existing_legacy_accounts())
                self.assertFalse(account_has_cloud_data(db_path, "empty-account"))
                self.assertFalse(legacy_admin_token_allowed(db_path, "empty-account"))

                upsert_device(db_path, "existing-account", "dev_a", "A", "2026-06-18T01:00:00Z")

                self.assertTrue(account_has_cloud_data(db_path, "existing-account"))
                self.assertTrue(legacy_admin_token_allowed(db_path, "existing-account"))

    def test_existing_legacy_account_compatibility_can_be_disabled(self):
        with tempfile.TemporaryDirectory() as tmp:
            db_path = Path(tmp) / "cloud.db"
            init_db(db_path)
            upsert_device(db_path, "existing-account", "dev_a", "A", "2026-06-18T01:00:00Z")

            with patch.dict("os.environ", {"HAOLEME_ALLOW_EXISTING_LEGACY_ACCOUNTS": "0"}, clear=True):
                self.assertFalse(server_allows_existing_legacy_accounts())
                self.assertFalse(legacy_admin_token_allowed(db_path, "existing-account"))

    def test_upsert_run_keeps_streamed_output_on_status_update(self):
        with tempfile.TemporaryDirectory() as tmp:
            db_path = Path(tmp) / "cloud.db"
            account_key = "account-key"
            device_id = "dev_1"
            init_db(db_path)
            upsert_run(db_path, account_key, self.sample_run("run-1", device_id, "Server A", "running"))
            auth = AuthContext(account_key=account_key, token_hash="h", scope="write",
                               device_id=device_id, device_name="Server A")
            append_run_update(db_path, account_key, {
                "id": "run-1", "status": "running",
                "e2eeOutputChunk": {"v": 1, "alg": "AES-256-GCM", "nonce": "n", "ciphertext": "abc"},
                "outputLength": 10,
            }, auth)
            # Final completion upsert carries no outputChunks (include_output=False);
            # the streamed output must survive the status update.
            upsert_run(db_path, account_key, self.sample_run("run-1", device_id, "Server A", "succeeded"))
            with connect(db_path) as db:
                payload = json.loads(db.execute(
                    "SELECT payload FROM runs WHERE account_key = ? AND id = ?",
                    (account_key, "run-1"),
                ).fetchone()["payload"])
            self.assertEqual(payload["status"], "succeeded")
            self.assertEqual(len(payload.get("outputChunks") or []), 1)
            self.assertEqual(payload["outputChunks"][0]["ciphertext"], "abc")
            self.assertEqual(payload.get("outputLength"), 10)

    def sample_run(self, run_id, device_id, device_name, status="succeeded"):
        return {
            "id": run_id,
            "command": ["echo", "hello"],
            "commandText": "echo hello",
            "cwd": "/tmp",
            "status": status,
            "pid": 123,
            "exitCode": 0 if status == "succeeded" else None,
            "startedAt": "2026-06-18T01:00:00Z",
            "endedAt": "2026-06-18T01:00:01Z" if status != "running" else None,
            "updatedAt": {
                "run-1": "2026-06-18T01:00:01Z",
                "run-2": "2026-06-18T01:05:01Z",
                "run-3": "2026-06-18T01:10:01Z",
            }.get(run_id, "2026-06-18T01:00:01Z"),
            "deviceId": device_id,
            "deviceName": device_name,
            "stdoutTail": "hello\n",
            "stderrTail": "",
            "outputTail": "hello\n",
        }


class CloudServerAppendTest(unittest.TestCase):
    def test_append_run_update_and_incremental_fetch(self):
        with tempfile.TemporaryDirectory() as tmp:
            db_path = Path(tmp) / "cloud.db"
            account_key = "account-key"
            init_db(db_path)
            writer = AuthContext(
                account_key=account_key,
                token_hash="write",
                scope="write",
                device_id="dev_123",
                device_name="CLI",
            )

            upsert_run(db_path, account_key, self.sample_run("run-1", "dev_123", "CLI", "running"))
            stored = append_run_update(
                db_path,
                account_key,
                {
                    "id": "run-1",
                    "status": "running",
                    "updatedAt": "2026-06-18T01:00:02Z",
                    "outputDelta": "more\n",
                    "outputLength": 10,
                },
                writer,
            )

            self.assertIsNotNone(stored)
            self.assertEqual(stored["outputTail"], "hello\nmore\n")
            self.assertEqual(stored["outputLength"], len("hello\nmore\n"))

            record_device_heartbeat(db_path, account_key, "dev_123", "CLI", iso_now())
            run = get_run(db_path, account_key, "run-1")
            payload = build_run_fetch_payload(run, output_length=len("hello\n"))
            self.assertTrue(payload["incremental"])
            self.assertEqual(payload["outputAppend"], "more\n")
            self.assertEqual(payload["run"]["outputTail"], "")

    def sample_run(self, run_id, device_id, device_name, status="succeeded"):
        return {
            "id": run_id,
            "command": ["echo", "hello"],
            "commandText": "echo hello",
            "cwd": "/tmp",
            "status": status,
            "pid": 123,
            "exitCode": 0 if status == "succeeded" else None,
            "startedAt": "2026-06-18T01:00:00Z",
            "endedAt": "2026-06-18T01:00:01Z" if status != "running" else None,
            "updatedAt": "2026-06-18T01:00:01Z",
            "deviceId": device_id,
            "deviceName": device_name,
            "stdoutTail": "hello\n",
            "stderrTail": "",
            "outputTail": "hello\n",
        }


if __name__ == "__main__":
    unittest.main()
