package com.playbox.games.ui.dictation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer

/** What the offline dictation engine is currently doing, for the on-screen hint. */
enum class DictationPhase { Off, Loading, Listening, Error, Unavailable }

fun Context.hasRecordPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/**
 * Offline dictation built on Vosk.
 *
 * The bundled Chinese model is unpacked from assets on first use and then kept in the app's files
 * directory. Recognition runs entirely on the device, so it works on phones without any system
 * speech service (many Chinese ROMs ship none) and without network access.
 *
 * The microphone is opened once per round and stays open for the whole round, so the child can
 * keep answering without waiting for the recorder to come back between questions. Audio capture
 * is driven by [AudioCapture] rather than Vosk's own `SpeechService`, whose reader thread throws
 * an uncaught `RuntimeException` when the recorder is stopped underneath it — which crashed the
 * app whenever a round was left halfway through.
 */
@Stable
class DictationEngine internal constructor(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    /** Set by the owning composable on every recomposition. */
    internal var onAnswers: (List<String>) -> Unit = {}

    /**
     * Words the recogniser may produce. The arithmetic tool passes its number grammar, the pinyin
     * tool the syllables of the current deck. Changing it while the microphone is open restarts
     * capture, so tools set it while they are idle.
     */
    internal var grammarJson: String = DictationModel.numberGrammarJson()

    var phase by mutableStateOf(DictationPhase.Off)
        private set

    /** Human readable explanation for the current error phase. */
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** False once the model could not be prepared, so the UI can say so. */
    var available by mutableStateOf(true)
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private var model: Model? = null
    private var capture: AudioCapture? = null
    private var loadingJob: Job? = null
    private var restartJob: Runnable? = null
    private var wanted = false
    private var destroyed = false

    /** Opens the microphone when [active] turns true and releases it when it turns false. */
    internal fun setActive(active: Boolean) {
        if (active == wanted) return
        wanted = active
        if (active) start() else stop()
    }

    private fun start() {
        if (destroyed || !wanted) return
        errorMessage = null
        model?.let { beginCapture(it); return }
        phase = DictationPhase.Loading
        loadingJob = scope.launch {
            val loaded = runCatching { withContext(Dispatchers.IO) { loadModel() } }
            loaded
                .onSuccess { prepared ->
                    if (destroyed || !wanted) {
                        runCatching { prepared.close() }
                        return@launch
                    }
                    model = prepared
                    beginCapture(prepared)
                }
                .onFailure { error ->
                    available = false
                    phase = DictationPhase.Unavailable
                    errorMessage = "语音模型准备失败：${error.message ?: "未知错误"}"
                }
        }
    }

    private fun beginCapture(loaded: Model) {
        if (destroyed || !wanted || capture != null) return
        val started = runCatching {
            AudioCapture(
                model = loaded,
                initialGrammarJson = grammarJson,
                onResult = { text -> post { applyResult(text) } },
                onFailed = { error -> post { applyError(error) } },
            ).also { capture = it; it.start() }
        }.getOrElse { error ->
            available = false
            phase = DictationPhase.Unavailable
            errorMessage = "语音识别初始化失败：${error.message ?: "未知错误"}"
            return
        }
        if (started.isAlive) phase = DictationPhase.Listening
    }

    private fun applyResult(result: String) {
        if (destroyed || !wanted) return
        phase = DictationPhase.Listening
        val utterance = DictationModel.parseUtterance(result)
        if (utterance.isNumber) onAnswers(listOf(utterance.text))
    }

    private fun applyError(error: Throwable) {
        if (destroyed || !wanted) return
        phase = DictationPhase.Error
        errorMessage = error.message?.takeIf { it.isNotBlank() } ?: "录音读取失败，正在重试"
        // The reader has already given up; open the microphone again shortly.
        releaseCapture()
        val retry = Runnable {
            restartJob = null
            if (!destroyed && wanted) start()
        }
        restartJob = retry
        mainHandler.postDelayed(retry, RESTART_DELAY_MILLIS)
    }

    private fun stop() {
        phase = if (available) DictationPhase.Off else DictationPhase.Unavailable
        releaseCapture()
    }

    /** Ends the reader thread and waits for it; the thread frees its own recogniser. */
    private fun releaseCapture() {
        restartJob?.let { mainHandler.removeCallbacks(it) }
        restartJob = null
        val running = capture ?: return
        capture = null
        running.stopCapture()
        running.joinSafely()
    }

    internal fun destroy() {
        if (destroyed) return
        destroyed = true
        wanted = false
        loadingJob?.cancel()
        loadingJob = null
        releaseCapture()
        val loaded = model
        model = null
        runCatching { loaded?.close() }
    }

    /**
     * Discards the audio buffered while judging was paused.
     *
     * The recorder runs for the whole round, so "pausing" dictation between questions only means
     * ignoring results. Clearing the buffer on resume keeps the tail of the previous question from
     * being judged against the next one, and costs nothing compared with reopening the microphone.
     */
    internal fun flushPendingAudio() {
        capture?.requestFlush()
    }

    /**
     * Swaps the words the recogniser listens for.
     *
     * A verification-style tool (the pinyin reader) narrows the vocabulary to the card in front of
     * the child, which is far more accurate than listening for the whole round at once. The swap
     * happens on the capture thread, so the microphone never closes.
     */
    internal fun setGrammar(json: String) {
        grammarJson = json
        capture?.requestGrammar(json)
    }

    private fun post(block: () -> Unit) {
        mainHandler.post { if (!destroyed) block() }
    }

    /** Unpacks the bundled model once and opens it. */
    private fun loadModel(): Model {
        val directory = DictationModel.prepare(context)
        return Model(directory.absolutePath)
    }

    private companion object {
        const val RESTART_DELAY_MILLIS = 800L
    }
}

/**
 * Streams 16 kHz mono audio into a Vosk [Recognizer] on its own thread.
 *
 * The recogniser lives here rather than in the engine so that everything touching it happens on
 * one thread: swapping the vocabulary and freeing the native objects can never race with a read
 * that is in flight. Every failure the reader can produce is contained, because the app used to be
 * taken down by `AudioRecord.read` throwing while the recorder was being released.
 */
private class AudioCapture(
    private val model: Model,
    initialGrammarJson: String,
    private val onResult: (String) -> Unit,
    private val onFailed: (Throwable) -> Unit,
) : Thread("playbox-audio-capture") {

    @Volatile
    private var running = true

    @Volatile
    private var recorder: AudioRecord? = null

    @Volatile
    private var pendingGrammar: String? = null

    @Volatile
    private var flushRequested = false

    private var recognizer: Recognizer? = null
    private var grammarJson: String = initialGrammarJson

    /** Ends the loop and unblocks a pending read. Safe to call from any thread. */
    fun stopCapture() {
        running = false
        runCatching { recorder?.stop() }
    }

    /** Waits for the reader to finish so the native objects can be freed safely afterwards. */
    fun joinSafely() {
        runCatching { join(CAPTURE_JOIN_TIMEOUT_MILLIS) }
    }

    /** Asks the reader to listen for a different set of words from the next buffer onwards. */
    fun requestGrammar(json: String) {
        pendingGrammar = json
    }

    /** Asks the reader to drop what it has buffered so far. */
    fun requestFlush() {
        flushRequested = true
    }

    override fun run() {
        val created = runCatching { Recognizer(model, SAMPLE_RATE_HZ, grammarJson) }.getOrElse { error ->
            onFailed(error)
            return
        }
        recognizer = created
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBufferSize <= 0) {
            runCatching { created.close() }
            onFailed(IllegalStateException("设备不支持 16kHz 录音"))
            return
        }
        val bufferSize = maxOf(minBufferSize, SAMPLE_RATE / 5 * BYTES_PER_SAMPLE)
        val record = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL,
                ENCODING,
                bufferSize * 2,
            )
        }.getOrElse { error ->
            runCatching { created.close() }
            onFailed(error)
            return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { record.release() }
            runCatching { created.close() }
            onFailed(IllegalStateException("麦克风被占用，无法开始录音"))
            return
        }
        recorder = record
        val buffer = ByteArray(bufferSize)
        var consecutiveFailures = 0
        try {
            record.startRecording()
            while (running) {
                val read = record.read(buffer, 0, buffer.size)
                swapGrammarIfRequested()
                if (flushRequested) {
                    flushRequested = false
                    runCatching { recognizer?.reset() }
                }
                if (read > 0) {
                    consecutiveFailures = 0
                    val active = recognizer ?: continue
                    if (active.acceptWaveForm(buffer, read)) {
                        onResult(active.result)
                    }
                } else if (running) {
                    consecutiveFailures += 1
                    if (consecutiveFailures >= MAX_READ_FAILURES) {
                        onFailed(IllegalStateException("麦克风读取失败"))
                        return
                    }
                }
            }
        } catch (error: Throwable) {
            if (running) onFailed(error)
        } finally {
            runCatching { if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop() }
            runCatching { record.release() }
            recorder = null
            recognizer?.let { stream -> runCatching { stream.close() } }
            recognizer = null
        }
    }

    private fun swapGrammarIfRequested() {
        val requested = pendingGrammar ?: return
        pendingGrammar = null
        if (requested == grammarJson) return
        runCatching {
            val fresh = Recognizer(model, SAMPLE_RATE_HZ, requested)
            recognizer?.let { old -> runCatching { old.close() } }
            recognizer = fresh
            grammarJson = requested
        }
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val SAMPLE_RATE_HZ = 16_000f
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val BYTES_PER_SAMPLE = 2
        const val MAX_READ_FAILURES = 10
        const val CAPTURE_JOIN_TIMEOUT_MILLIS = 3_000L
    }
}

/**
 * Remembers a [DictationEngine] and starts or stops it as [active] changes. The life cycle is
 * owned by one effect, and it is always stopped before the native model is released.
 */
@Composable
fun rememberDictationEngine(
    active: Boolean,
    onAnswers: (List<String>) -> Unit,
    grammarJson: String = DictationModel.numberGrammarJson(),
): DictationEngine {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember(context) { DictationEngine(context.applicationContext, scope) }
    val latestOnAnswers by rememberUpdatedState(onAnswers)
    SideEffect { engine.onAnswers = { latestOnAnswers(it) } }
    DisposableEffect(engine) {
        onDispose {
            // Stop the microphone first, then free the model: releasing it while audio is still
            // being read is what used to take the process down.
            engine.setActive(false)
            engine.destroy()
        }
    }
    DisposableEffect(engine, active) {
        // The grammar has to be in place before capture starts: a new round must not be recognised
        // with the previous round's words.
        engine.grammarJson = grammarJson
        engine.setActive(active)
        onDispose { }
    }
    return engine
}
