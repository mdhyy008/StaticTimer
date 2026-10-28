#!/usr/bin/env python3
"""极简 zipalign：把 resources.arsc / *.so 以 STORED 写入，并让每个条目数据 4 字节对齐。

Android 11+ 对 targetSdk >= 30 的 APK 强制要求 resources.arsc 不压缩且 4 字节对齐，
本机没有 zipalign 二进制，所以用 Python 重写一遍 zip。
"""
import struct
import sys
import zipfile

ALIGN_EXTRA_ID = 0xD935  # Android 自己用的对齐扩展字段 ID


def padding_for(base: int, align: int) -> bytes:
    """返回一段 extra 字节，使 (base + len(extra)) % align == 0。"""
    pad = (-base) % align
    if pad == 0:
        return b""
    if pad < 4:  # extra field 至少要有 4 字节头
        pad += align
    return struct.pack("<HH", ALIGN_EXTRA_ID, pad - 4) + b"\x00" * (pad - 4)


def main() -> None:
    src, dst = sys.argv[1], sys.argv[2]
    align = int(sys.argv[3]) if len(sys.argv) > 3 else 4

    with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w") as zout:
        for info in zin.infolist():
            data = zin.read(info.filename)
            ni = zipfile.ZipInfo(info.filename, info.date_time)
            ni.compress_type = (
                zipfile.ZIP_STORED
                if info.filename == "resources.arsc" or info.filename.endswith(".so")
                else zipfile.ZIP_DEFLATED
            )
            ni.external_attr = info.external_attr
            ni.create_system = info.create_system

            # 本条目 local header 结束、数据开始处的偏移
            base = zout.fp.tell() + 30 + len(info.filename.encode("utf-8"))
            ni.extra = padding_for(base, align)
            zout.writestr(ni, data)


if __name__ == "__main__":
    main()
