package com.playbox.games.ui.dictation

import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import org.json.JSONObject

/** Token the decoder emits when the audio does not match any word in the number grammar. */
internal const val UnknownToken = "[unk]"

/** One decoded utterance, with the raw words the recogniser produced. */
internal data class RecognizedUtterance(val text: String) {
    /**
     * True when the audio really was a number. Anything that does not sound like a number comes
     * back as [UnknownToken] instead, so unrelated chatter is never judged as a wrong answer.
     */
    val isNumber: Boolean get() = text.isNotBlank() && !text.contains(UnknownToken)
}

/**
 * The bundled offline speech model: unpacking it from assets and the grammar that limits
 * recognition to Chinese number words.
 *
 * Kept separate from the engine so the instrumentation test can exercise the very same model,
 * grammar and parser that the app uses at runtime.
 */
internal object DictationModel {
    const val SampleRate = 16_000f

    private const val AssetName = "vosk-model-cn.zip"
    private const val DirectoryName = "vosk-model-cn"
    private const val ReadyMarker = ".ready"

    /** Directory holding an unpacked model, extracting it from assets on first use. */
    fun prepare(context: Context): File {
        val target = File(context.filesDir, DirectoryName)
        if (!File(target, ReadyMarker).exists()) {
            if (target.exists()) target.deleteRecursively()
            target.mkdirs()
            unpack(context, target)
            File(target, ReadyMarker).createNewFile()
        }
        return target
    }

    private fun unpack(context: Context, target: File) {
        val targetPath = target.canonicalPath
        context.assets.open(AssetName).use { raw ->
            ZipInputStream(BufferedInputStream(raw)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    // The archive holds one top level folder; the model is unpacked directly into
                    // the target directory instead.
                    val relative = entry.name.substringAfter('/', "")
                    if (relative.isNotEmpty()) {
                        val output = File(target, relative)
                        // Guard against entries that try to escape the target directory.
                        if (output.canonicalPath.startsWith(targetPath)) {
                            if (entry.isDirectory) {
                                output.mkdirs()
                            } else {
                                output.parentFile?.mkdirs()
                                FileOutputStream(output).use { file -> zip.copyTo(file, 64 * 1024) }
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
    }

    /**
     * Numbers only: every word the child can say as an answer, plus [UnknownToken] so the decoder
     * can say "that was not a number" instead of forcing unrelated speech into a number.
     */
    fun numberGrammarJson(): String {
        val words = linkedSetOf(
            "零", "一", "二", "两", "三", "四", "五", "六", "七", "八", "九", "十", "百",
        )
        for (unit in 1..9) words += "十${digitWord(unit)}"
        for (tens in 2..9) {
            words += "${digitWord(tens)}十"
            for (unit in 1..9) words += "${digitWord(tens)}十${digitWord(unit)}"
        }
        words += "一百"
        words += UnknownToken
        return words.joinToString(prefix = "[", postfix = "]", separator = ",") { "\"$it\"" }
    }

    /** Reads the decoded words out of one recognition result. */
    fun parseUtterance(hypothesis: String?): RecognizedUtterance {
        if (hypothesis.isNullOrBlank()) return RecognizedUtterance("")
        return runCatching {
            RecognizedUtterance(JSONObject(hypothesis).optString("text").trim())
        }.getOrDefault(RecognizedUtterance(""))
    }

    private fun digitWord(value: Int): String = "零一二三四五六七八九"[value].toString()
}
