#!/bin/sh
# Regenerates the Chinese speech samples used by DictationRecognitionTest.
#
# The samples are synthesised with the macOS `say` command, because the test has to feed known
# utterances into the bundled offline recogniser without a human speaking into the phone.
# Requires macOS; run it from the repository root.
#
#   sh tools/generate-speech-samples.sh
#
set -e

VOICE="${VOICE:-Tingting}"
OUTPUT_DIR="$(dirname "$0")/../app/src/androidTest/assets/numbers"
TEMP_FILE="$(mktemp -t playbox-tts).aiff"

mkdir -p "$OUTPUT_DIR"

synthesise() {
    # $1 = output name (without extension), $2 = text to speak
    say -v "$VOICE" -o "$TEMP_FILE" "$2"
    afconvert -f WAVE -d LEI16@16000 -c 1 "$TEMP_FILE" "$OUTPUT_DIR/$1.wav"
    echo "wrote $OUTPUT_DIR/$1.wav  ($2)"
}

synthesise n0 "零"
synthesise n4 "四"
synthesise n5 "五"
synthesise n7 "七"
synthesise n10 "十"
synthesise n12 "十二"
synthesise n15 "十五"
synthesise n23 "二十三"
synthesise n38 "三十八"
synthesise n60 "六十"
synthesise n99 "九十九"
synthesise n100 "一百"
# Phrases that are not answers: the grammar must report these as [unk] instead of turning them
# into number words.
synthesise not-a-number "苹果真好吃"
synthesise noise-unknown "我不知道"
synthesise noise-mother "妈妈"
synthesise noise-question "这个怎么算"

# Syllables for the pinyin tool (PinyinRecognitionTest), written into assets/zimu.
OUTPUT_DIR="$(dirname "$0")/../app/src/androidTest/assets/zimu"
mkdir -p "$OUTPUT_DIR"
synthesise_zimu() {
    say -v "$VOICE" -o "$TEMP_FILE" "$2"
    afconvert -f WAVE -d LEI16@16000 -c 1 "$TEMP_FILE" "$OUTPUT_DIR/$1.wav"
    echo "wrote $OUTPUT_DIR/$1.wav  ($2)"
}
synthesise_zimu ma "妈"
synthesise_zimu ba "八"
synthesise_zimu shu "书"
synthesise_zimu chi "吃"

rm -f "$TEMP_FILE"
echo "done"
