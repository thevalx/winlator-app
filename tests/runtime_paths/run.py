#!/usr/bin/env python3
"""Validate production relocation and the exact byte changes in bundled official assets."""
from pathlib import Path
import shutil
import struct
import subprocess
import tarfile
import tempfile

ROOT = Path(__file__).resolve().parents[2]
ORIGINAL = b"/data/data/com.winlator/files/rootfs"
RELOCATED = b"/data/data/com.winlator.trig/files/r"


def elf_layout_and_code(data):
    """ELF metadata and executable sections must survive path-only relocation unchanged."""
    if not data.startswith(b"\x7fELF"):
        return ()
    endian = "<" if data[5] == 1 else ">"
    if data[4] == 2:
        phoff, shoff = struct.unpack_from(endian + "QQ", data, 32)
        ehsize, phentsize, phnum, shentsize, shnum = struct.unpack_from(endian + "HHHHH", data, 52)
        sections = [struct.unpack_from(endian + "IIQQQQIIQQ", data, shoff + i * shentsize)
                    for i in range(shnum)]
    else:
        phoff, shoff = struct.unpack_from(endian + "II", data, 28)
        ehsize, phentsize, phnum, shentsize, shnum = struct.unpack_from(endian + "HHHHH", data, 40)
        sections = [struct.unpack_from(endian + "IIIIIIIIII", data, shoff + i * shentsize)
                    for i in range(shnum)]
    executable = tuple(data[section[4]:section[4]+section[5]]
                       for section in sections if section[2] & 0x4)
    return (data[:ehsize], data[phoff:phoff+phentsize*phnum],
            data[shoff:shoff+shentsize*shnum], executable)


def main():
    javac = shutil.which("javac")
    java = shutil.which("java")
    zstd = shutil.which("zstd")
    if not all((javac, java, zstd)):
        raise SystemExit("A JDK and zstd are required for runtime path checks")
    with tempfile.TemporaryDirectory(prefix="winlator-runtime-paths-") as temporary:
        work = Path(temporary)
        subprocess.run([javac, "-d", str(work),
                        str(ROOT / "app/src/main/java/com/winlator/core/RootFSPathRelocator.java"),
                        str(ROOT / "tests/runtime_paths/RootFSPathRelocatorTest.java")], check=True)
        subprocess.run([java, "-cp", str(work), "RootFSPathRelocatorTest"], check=True)
        assets = ROOT / "app/src/main/assets"
        samples = []
        archives = sorted(assets.rglob("*.tzst"))
        link_count = 0
        for archive in archives:
            decompressor = subprocess.Popen([zstd, "-dc", str(archive)], stdout=subprocess.PIPE)
            try:
                with tarfile.open(fileobj=decompressor.stdout, mode="r|") as contents:
                    for entry in contents:
                        if entry.issym() and ORIGINAL.decode() in entry.linkname:
                            link_count += 1
                        if not entry.isfile():
                            continue
                        stream = contents.extractfile(entry)
                        data = stream.read()
                        if ORIGINAL not in data:
                            continue
                        sample = work / (str(len(samples)) + ".original")
                        sample.write_bytes(data)
                        samples.append((sample, str(archive.relative_to(assets)), entry.name))
            finally:
                decompressor.stdout.close()
                if decompressor.wait() != 0:
                    raise SystemExit("Unable to decompress " + str(archive))
        if not samples:
            raise AssertionError("No official rootfs paths were found in bundled runtime assets")
        subprocess.run([java, "-cp", str(work), "RootFSPathRelocatorTest", str(work)], check=True)
        replacements = 0
        elf_count = 0
        for sample, archive, name in samples:
            before = sample.read_bytes()
            after = Path(str(sample) + ".relocated").read_bytes()
            assert len(before) == len(after), (archive, name, "file length changed")
            assert after == before.replace(ORIGINAL, RELOCATED), (archive, name, "unexpected bytes changed")
            assert ORIGINAL not in after, (archive, name, "official path remains")
            assert elf_layout_and_code(before) == elf_layout_and_code(after), (archive, name, "ELF layout/code changed")
            replacements += before.count(ORIGINAL)
            elf_count += before.startswith(b"\x7fELF")
        # The extraction hook must cover every source overload and both archive links/files.
        source = (ROOT / "app/src/main/java/com/winlator/core/TarCompressorUtils.java").read_text()
        assert "ROOTFS_PATH_RELOCATOR.copy(tar, outStream)" in source
        assert "ROOTFS_PATH_RELOCATOR.relocateLink(entry.getLinkName())" in source
        print(f"Runtime assets: {replacements} path replacements in {len(samples)} files from {len(archives)} archives; "
              f"{elf_count} ELF layouts and executable sections unchanged; {link_count} absolute official archive links")


if __name__ == "__main__":
    main()
