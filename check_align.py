#!/usr/bin/env python3
"""校验 APK：条目可读、resources.arsc 未压缩且 4 字节对齐。"""
import sys
import zipfile


def data_offset(info: zipfile.ZipInfo) -> int:
    return info.header_offset + 30 + len(info.filename.encode("utf-8")) + len(info.extra)


def main() -> int:
    path = sys.argv[1]
    bad = []
    with zipfile.ZipFile(path) as z:
        broken = z.testzip()
        if broken:
            print(f"损坏条目：{broken}", file=sys.stderr)
            return 1
        for info in z.infolist():
            if info.filename == "resources.arsc":
                off = data_offset(info)
                stored = info.compress_type == zipfile.ZIP_STORED
                aligned = off % 4 == 0
                print(f"resources.arsc: stored={stored} offset={off} aligned4={aligned}")
                if not (stored and aligned):
                    bad.append("resources.arsc 需不压缩且 4 字节对齐")
            elif info.filename.endswith(".so"):
                off = data_offset(info)
                if off % 4096:
                    bad.append(f"{info.filename} 未按 4096 对齐（本 Demo 无 .so，可忽略）")
    if bad:
        print("校验失败：" + "; ".join(bad), file=sys.stderr)
        return 1
    print("对齐校验通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
