package lib.fetchmoodle

import com.fleeksoft.ksoup.nodes.Element

object MoodleLog {
    enum class Level(val value: String) { DEBUG("D"), INFO("I"), WARN("W"), ERROR("E") }

    /**
     * Data class holding log information.
     * Cannot be a `data class` because of the `vararg` constructor parameter.
     *
     * @property level The severity level of the log message.
     * @property tag A tag identifying the source of the log message (e.g., class name).
     * @property messages The content of the log message(s). The last element might be a Throwable for ERROR level.
     */
    class LogLine(val level: Level, val tag: String, vararg val messages: Any?) {
    }

    /**
     * The default logger implementation that prints to standard out/err.
     * Handles the convention that the last argument for ERROR might be a Throwable.
     */
    private val defaultLogger: (LogLine) -> Unit = { log ->
        var throwable: Throwable? = null
        val messageContent: List<Any?>

        // Check if the last argument for an ERROR log is a Throwable
        if (log.level == Level.ERROR && log.messages.isNotEmpty() && log.messages.last() is Throwable) {
            throwable = log.messages.last() as Throwable
            // Use all messages except the last one for the main log string
            messageContent = log.messages.dropLast(1)
        } else {
            // Use all messages for the main log string
            messageContent = log.messages.toList() // Convert vararg to list for consistent handling
        }

        // Format the message part by joining the non-throwable arguments
        val messageString = messageContent.joinToString(" ") { it?.toString() ?: "null" }

        // Prepare the final log line
        val logLine = "[${log.level.name}] ${log.tag} > $messageString"

        println(logLine)

        // Print stack trace if a throwable was found
        throwable?.let {
            println(it.stackTraceToString())
        }
    }

    /** The currently active logger function. Initially set to the default logger. */
    @kotlin.concurrent.Volatile // Ensure visibility across threads, though assignment isn't atomic
    private var currentLogger: (LogLine) -> Unit = defaultLogger

    /**
     * Sets a logger implementation.
     * When a logger is set (not null), the default logger is disabled,
     * and all log messages are routed to the provided logger.
     * If `null` is passed, the logger reverts to the default one.
     *
     * @param logger The logger function `(TeleLogger.Log) -> Unit`, or `null` to reset to default.
     */
    internal fun setLogger(logger: ((LogLine) -> Unit)?) {
        currentLogger = logger ?: defaultLogger
    }

    /** Logs a DEBUG message. */
    fun d(tag: String, vararg messages: Any?) {
        // Optimization: Could check log level here if the logger supported it,
        // but for now, always create the Log object and delegate.
        currentLogger(LogLine(Level.DEBUG, tag, *messages)) // Use spread operator (*)
    }

    /** Logs an INFO message. */
    fun i(tag: String, vararg messages: Any?) {
        currentLogger(LogLine(Level.INFO, tag, *messages))
    }

    /** Logs a WARN message. */
    fun w(tag: String, vararg messages: Any?) {
        currentLogger(LogLine(Level.WARN, tag, *messages))
    }

    /**
     * Logs an ERROR message.
     * Conventionally, the last argument in `messages` may be a `Throwable`.
     * The active logger implementation is responsible for handling this.
     */
    fun e(tag: String, vararg messages: Any?) {
        currentLogger(LogLine(Level.ERROR, tag, *messages))
    }
}

object MoodleUtils {
    private const val TAG = "MoodleUtils"
}

object JsoupUtils {
    private const val TAG = "JsoupUtils"

    val Element.allText get() = wholeText().replace("\r\n", "\n").trim()
}
