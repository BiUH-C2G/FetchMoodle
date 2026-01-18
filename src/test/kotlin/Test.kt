import kotlinx.coroutines.runBlocking
import lib.fetchmoodle.MoodleFetcher
import lib.fetchmoodle.MoodleFetcherConfig
import lib.fetchmoodle.MoodleLog
import lib.fetchmoodle.MoodleResult
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName::class)
class MoodleTest {
    companion object {
        private const val TAG = "MoodleTest"
    }

    val moodleFetcher = MoodleFetcher(MoodleFetcherConfig(baseUrl = "https://school.moodledemo.net"))

    @Test
    fun test01_login() = runBlocking {
        when (val res = moodleFetcher.login("student", "moodle25")) {
            is MoodleResult.Success -> MoodleLog.i(TAG, "登录成功")

            is MoodleResult.Failure -> MoodleLog.e(TAG, "登录失败：${res.exception.stackTraceToString()}")
        }
    }

    @Test
    fun test02_getGrades() = runBlocking {
        when (val moodleResult = moodleFetcher.getGrades()) {
            is MoodleResult.Success -> MoodleLog.i(TAG, "获取成绩成功：${moodleResult.data}")

            is MoodleResult.Failure -> MoodleLog.e(TAG, "获取成绩失败：${moodleResult.exception.stackTraceToString()}")
        }
    }
}