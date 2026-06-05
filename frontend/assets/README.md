# assets

Stable retained asset material extracted from `_work/apktool_smali` and `_work/apktool_res_2x`.

## Contents

- `apktool_smali/assets/`: raw APK asset tree from the smali apktool decode.
- `apktool_smali/unknown/`: files apktool preserved as unknown during the smali decode.
- `apktool_smali/resources.arsc`: binary Android resource table from the smali decode.
- `apktool_res_2x/res/`: decoded Android resource tree, including image, font, raw, vector XML, and resource XML files.
- `apktool_res_2x/lib/`: original native shared libraries.
- `apktool_res_2x/unknown/`: apktool unknown files from the 2x resource decode.
- `apktool_res_2x/original/`: original manifest and signing metadata preserved by apktool.
- `apktool_res_2x/AndroidManifest.xml`: decoded manifest from the 2x resource decode.

Reports for the extraction are in `../docs/reports/`.
