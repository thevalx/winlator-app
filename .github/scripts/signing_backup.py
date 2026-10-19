#!/usr/bin/env python3
"""Encrypt or restore a signing backup without logging its contents or password.

Version 1 uses OpenSSL AES-256-CBC with PBKDF2-HMAC-SHA256 (600,000
iterations), followed by encrypt-then-HMAC-SHA256 over the entire envelope.
The authentication key has an independent, domain-separated PBKDF2 salt.
Authentication is checked before decryption. Passwords reach OpenSSL through
a private pipe, never a command-line argument or a temporary password file.
Only an encrypted envelope may be uploaded; decrypted output stays private.
"""

import argparse
import functools
import hashlib
import hmac
import os
from pathlib import Path
import secrets
import stat
import struct
import subprocess
import sys
import tempfile


SECRET_NAME = "TRIG_SIGNING_BACKUP_PASSWORD"
MAGIC = b"WTRIGSB\0"
VERSION = 1
ITERATIONS = 600_000
HEADER = struct.Struct(">8sBI32sQ")
MAC_SIZE = 32
MAX_PLAINTEXT = 16 * 1024 * 1024
MAX_ENVELOPE = MAX_PLAINTEXT + 1024
MAC_DOMAIN = b"Winlator +trig signing backup authentication v1\0"


class BackupError(Exception):
    pass


def password_from_environment():
    value = os.environ.get(SECRET_NAME, "")
    if not value:
        raise BackupError(
            f"Missing {SECRET_NAME}. Add a strong password as a repository "
            "Actions secret in GitHub Settings > Secrets and variables > Actions, "
            "then rerun this workflow. Do not send the password in chat."
        )
    if len(value) < 20 or len(value.encode("utf-8")) > 1024:
        raise BackupError(f"{SECRET_NAME} needs at least 20 characters and at most 1024 UTF-8 bytes.")
    if any(character in value for character in ("\r", "\n", "\0")):
        raise BackupError(f"{SECRET_NAME} must be a single line without null bytes.")
    return value.encode("utf-8")


@functools.lru_cache(maxsize=1)
def openssl_salt_options():
    # OpenSSL 3.2 changed enc's default salt length from 8 to 16 bytes.
    # Fix it at 8 for compatibility with Ubuntu 24.04's OpenSSL 3.0 and
    # authenticate that salt along with the complete encrypted payload.
    try:
        result = subprocess.run(
            ["openssl", "enc", "-help"], capture_output=True, check=True, timeout=10,
            env={name: value for name, value in os.environ.items() if name != SECRET_NAME},
        )
    except (OSError, subprocess.SubprocessError) as error:
        raise BackupError("OpenSSL enc is required for signing backups.") from error
    return ["-saltlen", "8"] if b"-saltlen" in result.stderr + result.stdout else []


def openssl_cipher(data, password, decrypt=False):
    read_fd, write_fd = os.pipe()
    try:
        os.write(write_fd, password + b"\n")
        os.close(write_fd)
        write_fd = None
        command = [
            "openssl", "enc", "-aes-256-cbc", "-pbkdf2", "-md", "sha256",
            "-iter", str(ITERATIONS), "-pass", f"fd:{read_fd}",
            *openssl_salt_options(),
        ]
        if decrypt:
            command.append("-d")
        result = subprocess.run(
            command, input=data, capture_output=True, pass_fds=(read_fd,), check=True,
            timeout=60,
            env={name: value for name, value in os.environ.items() if name != SECRET_NAME},
        )
        return result.stdout
    except (OSError, subprocess.SubprocessError) as error:
        # Never echo subprocess arguments, stderr, plaintext, or passwords.
        raise BackupError("OpenSSL signing backup operation failed.") from error
    finally:
        os.close(read_fd)
        if write_fd is not None:
            os.close(write_fd)


def authentication_key(password, salt):
    return hashlib.pbkdf2_hmac(
        "sha256", password, MAC_DOMAIN + salt, ITERATIONS, dklen=32
    )


def encrypt_bytes(plaintext, password):
    if len(plaintext) > MAX_PLAINTEXT:
        raise BackupError("Signing input exceeds the 16 MiB size limit.")
    salt = secrets.token_bytes(32)
    ciphertext = openssl_cipher(plaintext, password)
    header = HEADER.pack(MAGIC, VERSION, ITERATIONS, salt, len(ciphertext))
    authenticated = header + ciphertext
    return authenticated + hmac.digest(
        authentication_key(password, salt), authenticated, "sha256"
    )


def decrypt_bytes(envelope, password):
    if not HEADER.size + MAC_SIZE + 32 <= len(envelope) <= MAX_ENVELOPE:
        raise BackupError("Invalid signing backup length.")
    magic, version, iterations, salt, size = HEADER.unpack(envelope[:HEADER.size])
    if magic != MAGIC or version != VERSION or iterations != ITERATIONS:
        raise BackupError("Unsupported or damaged signing backup header.")
    if size != len(envelope) - HEADER.size - MAC_SIZE:
        raise BackupError("Invalid signing backup payload length.")
    authenticated, supplied_mac = envelope[:-MAC_SIZE], envelope[-MAC_SIZE:]
    expected_mac = hmac.digest(
        authentication_key(password, salt), authenticated, "sha256"
    )
    if not hmac.compare_digest(supplied_mac, expected_mac):
        raise BackupError("Signing backup authentication failed: wrong password or damaged backup.")
    ciphertext = envelope[HEADER.size:-MAC_SIZE]
    if not ciphertext.startswith(b"Salted__") or (len(ciphertext) - 16) % 16:
        raise BackupError("Invalid authenticated signing backup payload.")
    return openssl_cipher(ciphertext, password, decrypt=True)


def read_limited(path, limit):
    with Path(path).open("rb") as stream:
        data = stream.read(limit + 1)
    if len(data) > limit:
        raise BackupError("Signing backup input exceeds its size limit.")
    return data


def write_private(path, contents):
    # Exclusive creation refuses to overwrite files or follow output symlinks.
    # Callers receive no output file until encryption/authentication succeeded.
    descriptor = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(contents)
    except BaseException:
        Path(path).unlink(missing_ok=True)
        raise


def transform(source, destination, password, decrypt=False):
    data = read_limited(source, MAX_ENVELOPE if decrypt else MAX_PLAINTEXT)
    result = decrypt_bytes(data, password) if decrypt else encrypt_bytes(data, password)
    write_private(destination, result)


def self_test():
    """Use random dummy bytes only; never access a real signing key or secret."""
    password = secrets.token_hex(24).encode("ascii")
    plaintext = secrets.token_bytes(4099)
    envelope = encrypt_bytes(plaintext, password)
    if decrypt_bytes(envelope, password) != plaintext:
        raise BackupError("Dummy round-trip failed.")
    if encrypt_bytes(plaintext, password) == envelope:
        raise BackupError("Encryption did not randomize the backup.")
    failures = [
        (envelope, password + b"wrong"),
        (envelope[:-1] + bytes([envelope[-1] ^ 1]), password),
        (envelope[:HEADER.size + 20] + bytes([envelope[HEADER.size + 20] ^ 1])
         + envelope[HEADER.size + 21:], password),
        (envelope[:20] + bytes([envelope[20] ^ 1]) + envelope[21:], password),
        (envelope[:-1], password),
        (envelope + b"extra", password),
        (b"damaged", password),
    ]
    for damaged, supplied_password in failures:
        try:
            decrypt_bytes(damaged, supplied_password)
        except BackupError:
            pass
        else:
            raise BackupError("Invalid dummy backup was accepted.")
    with tempfile.TemporaryDirectory(prefix="trig-backup-self-test-") as directory:
        source = Path(directory) / "dummy-input"
        encrypted = Path(directory) / "dummy.enc"
        restored = Path(directory) / "dummy-restored"
        source.write_bytes(plaintext)
        transform(source, encrypted, password)
        transform(encrypted, restored, password, decrypt=True)
        if restored.read_bytes() != plaintext or stat.S_IMODE(restored.stat().st_mode) != 0o600:
            raise BackupError("Private dummy file round-trip failed.")
        rejected = Path(directory) / "must-not-exist"
        try:
            transform(encrypted, rejected, password + b"wrong", decrypt=True)
        except BackupError:
            if rejected.exists():
                raise BackupError("Failed authentication created an output file.")
        else:
            raise BackupError("Wrong dummy password was accepted.")
        try:
            write_private(restored, b"replacement")
        except FileExistsError:
            pass
        else:
            raise BackupError("Existing output was overwritten.")
    print("Signing backup self-test passed: random round-trip, tamper/wrong-password rejection, private output.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("self-test")
    commands.add_parser("check-password")
    for name in ("encrypt", "decrypt"):
        command = commands.add_parser(name)
        command.add_argument("--input", required=True, type=Path)
        command.add_argument("--output", required=True, type=Path)
    arguments = parser.parse_args()
    try:
        if arguments.command == "self-test":
            self_test()
        elif arguments.command == "check-password":
            password_from_environment()
            print("Signing backup password is configured.")
        else:
            transform(
                arguments.input, arguments.output, password_from_environment(),
                decrypt=arguments.command == "decrypt",
            )
            print("Signing backup restored privately." if arguments.command == "decrypt"
                  else "Encrypted signing backup created.")
    except (BackupError, OSError) as error:
        print(f"Signing backup error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
