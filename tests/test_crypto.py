import unittest

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding

from haoleme.crypto import (
    b64url_decode,
    b64url_encode,
    decrypt_account_key,
    decrypt_output_chunk,
    decrypt_run_payload,
    encrypt_output_chunk,
    encrypt_run_payload,
    generate_account_key,
    generate_pair_keypair,
    is_valid_account_key,
)


class CryptoTest(unittest.TestCase):
    def test_pair_key_wrap_roundtrip(self):
        public_pem, private_pem = generate_pair_keypair()
        account_key = generate_account_key()
        public_key = serialization.load_pem_public_key(public_pem.encode("ascii"))

        encrypted = public_key.encrypt(
            b64url_decode(account_key),
            padding.OAEP(
                mgf=padding.MGF1(algorithm=hashes.SHA256()),
                algorithm=hashes.SHA256(),
                label=None,
            ),
        )

        self.assertEqual(decrypt_account_key(b64url_encode(encrypted), private_pem), account_key)

    def test_account_key_validation(self):
        self.assertTrue(is_valid_account_key(generate_account_key()))
        self.assertFalse(is_valid_account_key(""))
        self.assertFalse(is_valid_account_key(b64url_encode(b"too-short")))

    def test_run_payload_encrypts_sensitive_fields(self):
        account_key = generate_account_key()
        run = {
            "id": "run-1",
            "command": ["python", "-c", "print('secret')"],
            "commandText": "python -c 'print(secret)'",
            "cwd": "/private/project",
            "status": "succeeded",
            "pid": 123,
            "exitCode": 0,
            "startedAt": "2026-06-19T00:00:00Z",
            "endedAt": "2026-06-19T00:00:01Z",
            "updatedAt": "2026-06-19T00:00:01Z",
            "stdoutTail": "secret output",
            "stderrTail": "",
            "outputTail": "secret output",
        }

        encrypted = encrypt_run_payload(run, account_key)

        self.assertEqual(encrypted["commandText"], "Encrypted command")
        self.assertEqual(encrypted["stdoutTail"], "")
        self.assertNotIn("secret output", str(encrypted))
        self.assertIn("ciphertext", encrypted["e2ee"])
        self.assertEqual(decrypt_run_payload(encrypted, account_key)["outputTail"], "secret output")

    def test_output_chunk_roundtrip_and_merge(self):
        account_key = generate_account_key()
        run = {
            "id": "run-1",
            "command": ["echo", "hi"],
            "commandText": "echo hi",
            "cwd": "/tmp",
            "status": "running",
            "stdoutTail": "",
            "stderrTail": "",
            "outputTail": "",
        }
        encrypted = encrypt_run_payload(run, account_key, include_output=False)
        chunk = encrypt_output_chunk("run-1", account_key, {"outputTail": "line-1\n", "stdoutTail": "line-1\n"})
        encrypted["outputChunks"] = [chunk]
        decrypted = decrypt_run_payload(encrypted, account_key)
        self.assertEqual(decrypted["outputTail"], "line-1\n")
        self.assertEqual(
            decrypt_output_chunk("run-1", account_key, encrypt_output_chunk("run-1", account_key, {"outputTail": "line-2\n"})),
            {"outputTail": "line-2\n"},
        )


if __name__ == "__main__":
    unittest.main()
