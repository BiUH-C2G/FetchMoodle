package lib.fetchmoodle

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*

class MoodleFetcherConfig(
    val baseUrl: String
)

class MoodleContext(
    val httpClient: HttpClient,
    var baseUrl: String,
    var sesskey: String? = null,
    var moodleSession: String? = null
) {
    private companion object {
        const val TAG = "MoodleFetcher"
    }

    val isLoggedIn: Boolean; get() = !moodleSession.isNullOrEmpty() && !sesskey.isNullOrEmpty() // 缺一不可

    fun HttpRequestBuilder.injectMoodleSession() {
        moodleSession?.let {
            cookie("MoodleSession", it)
            MoodleLog.d(TAG, "已注入Moodle会话")
        }
    }
}

class MoodleFetcher(moodleFetcherConfig: MoodleFetcherConfig) {
    private val moodleContext = MoodleContext(HttpClient(CIO), moodleFetcherConfig.baseUrl)

    // 使用已有的会话数据登录
    fun loginBySessionData(sesskey: String, moodleSession: String) {
        moodleContext.sesskey = sesskey
        moodleContext.moodleSession = moodleSession
    }

    // 覆写基础URL
    fun setBaseUrl(baseUrl: String) {
        moodleContext.baseUrl = baseUrl
    }

    // TIPS：通用方法
    suspend fun <RESULT_TYPE> execute(operation: MoodleOperation<RESULT_TYPE>): MoodleResult<RESULT_TYPE> = with(operation) {
        moodleContext.execute()
    }

    suspend fun login(username: String, password: String): MoodleResult<Unit> = execute(LoginOperation(username, password))

    suspend fun getGrades() = execute(GradesQuery())

    companion object {
        private const val TAG = "MoodleFetcher"
    }
}

class MoodleException(message: String, cause: Throwable? = null) : Exception(message, cause)