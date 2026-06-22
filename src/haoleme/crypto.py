from __future__ import annotations

import base64
import json
import os
from typing import Any

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa
from cryptography.hazmat.primitives.ciphers.aead import AESGCM


E2EE_VERSION = 1
RUN_E2EE_ALGORITHM = "AES-256-GCM"
KEY_WRAP_ALGORITHM = "RSA-OAEP-SHA256"
ENCRYPTED_FIELDS = ("command", "commandText", "cwd", "stdoutTail", "stderrTail", "outputTail")


def b64url_encode(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).decode("ascii").rstrip("=")


def b64url_decode(value: str) -> bytes:
    padded = value.strip() + ("=" * (-len(value.strip()) % 4))
    return base64.urlsafe_b64decode(padded.encode("ascii"))


def generate_account_key() -> str:
    return b64url_encode(os.urandom(32))


def is_valid_account_key(value: str) -> bool:
    try:
        return len(b64url_decode(value)) == 32
    except Exception:
        return False


def generate_pair_keypair() -> tuple[str, bytes]:
    private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    public_key = private_key.public_key()
    public_pem = public_key.public_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    ).decode("ascii")
    private_pem = private_key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption(),
    )
    return public_pem, private_pem


def decrypt_account_key(encrypted_key: str, private_key_pem: bytes) -> str:
    private_key = serialization.load_pem_private_key(private_key_pem, password=None)
    plaintext = private_key.decrypt(
        b64url_decode(encrypted_key),
        padding.OAEP(
            mgf=padding.MGF1(algorithm=hashes.SHA256()),
            algorithm=hashes.SHA256(),
            label=None,
        ),
    )
    if len(plaintext) != 32:
        raise ValueError("invalid 好了么 encryption key")
    return b64url_encode(plaintext)


def encrypt_run_payload(run: dict[str, Any], account_key: str) -> dict[str, Any]:
    key = b64url_decode(account_key)
    if len(key) != 32:
        raise ValueError("好了么 encryption key must be 32 bytes")

    cleartext = {field: run.get(field) for field in ENCRYPTED_FIELDS}
    cleartext_bytes = json.dumps(cleartext, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    nonce = os.urandom(12)
    aad = str(run.get("id") or "").encode("utf-8")
    ciphertext = AESGCM(key).encrypt(nonce, cleartext_bytes, aad)

    encrypted = dict(run)
    encrypted["command"] = []
    encrypted["commandText"] = "Encrypted command"
    encrypted["cwd"] = ""
    encrypted["stdoutTail"] = ""
    encrypted["stderrTail"] = ""
    encrypted["outputTail"] = ""
    encrypted["e2ee"] = {
        "v": E2EE_VERSION,
        "alg": RUN_E2EE_ALGORITHM,
        "nonce": b64url_encode(nonce),
        "ciphertext": b64url_encode(ciphertext),
    }
    return encrypted


def decrypt_run_payload(run: dict[str, Any], account_key: str) -> dict[str, Any]:
    e2ee = run.get("e2ee")
    if not isinstance(e2ee, dict):
        return run
    if int(e2ee.get("v") or 0) != E2EE_VERSION:
        return run
    key = b64url_decode(account_key)
    nonce = b64url_decode(str(e2ee.get("nonce") or ""))
    ciphertext = b64url_decode(str(e2ee.get("ciphertext") or ""))
    aad = str(run.get("id") or "").encode("utf-8")
    cleartext = AESGCM(key).decrypt(nonce, ciphertext, aad)
    fields = json.loads(cleartext.decode("utf-8"))
    if not isinstance(fields, dict):
        return run
    decrypted = dict(run)
    for field in ENCRYPTED_FIELDS:
        if field in fields:
            decrypted[field] = fields[field]
    return decrypted
