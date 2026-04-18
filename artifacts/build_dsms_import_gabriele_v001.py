from __future__ import annotations

import gzip
from pathlib import Path


def main() -> None:
    root = Path(__file__).resolve().parent
    source_path = root / "dsms_import_gabriele_v001.dsms"
    target_path = root / "dsms_import_gabriele_v001_compressed.dsms"

    target_path.write_bytes(gzip.compress(source_path.read_bytes()))
    print(target_path)


if __name__ == "__main__":
    main()
