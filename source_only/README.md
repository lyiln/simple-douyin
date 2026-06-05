# source_only

This directory is the retained source-only baseline from the APK extraction.

## Contents

- `android_dex_smali/`: complete main APK DEX baseline in smali form.
- `nested_dex_smali/`: smali recovered from nested APK/DEX archives.
- `plugins/`: smali recovered from bundled plugin packages.
- `java_jadx/`: JADX Java output for higher-level pseudocode review.
- `android_resources/`: decoded Android resource XML and other source-like resources.
- `assets_source/`: text/source-like files recovered from APK assets and nested archives.
- `native_asm/`: disassembled native library text. Original native source cannot be exactly recovered from stripped `.so` binaries.

Binary assets and original binaries are intentionally kept outside this tree. See `../assets/` for retained usable assets and binary resource material.
