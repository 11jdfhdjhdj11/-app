#!/usr/bin/env bash
# 下载 Kokoro v1.1-zh（int8）离线模型到音色版资源目录。只需运行一次。
set -euo pipefail
cd "$(dirname "$0")/.."
DEST=app/src/full/assets/kokoro
NAME=kokoro-int8-multi-lang-v1_1
URL=https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$NAME.tar.bz2

if [ -f "$DEST/model.int8.onnx" ]; then
  echo "模型已存在：$DEST"; exit 0
fi
TMP=$(mktemp -d)
echo "下载 $NAME（约 140 MB）…"
curl -fL --retry 3 -o "$TMP/m.tar.bz2" "$URL"
tar xjf "$TMP/m.tar.bz2" -C "$TMP"
mkdir -p "$DEST"
SRC="$TMP/$NAME"
# 只保留中文播报需要的文件（不需要英式英语词典和 jieba 词典目录）
cp "$SRC"/model.int8.onnx "$SRC"/voices.bin "$SRC"/tokens.txt \
   "$SRC"/lexicon-zh.txt "$SRC"/lexicon-us-en.txt \
   "$SRC"/phone-zh.fst "$SRC"/date-zh.fst "$SRC"/number-zh.fst \
   "$SRC"/LICENSE "$DEST"/
cp -r "$SRC"/espeak-ng-data "$DEST"/
rm -rf "$TMP"
du -sh "$DEST"
echo "完成"
