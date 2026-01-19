import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import lib.fetchmoodle.CourseQuery
import lib.fetchmoodle.MoodleCourseRes
import lib.fetchmoodle.MoodleFetcher
import lib.fetchmoodle.MoodleLog
import lib.fetchmoodle.MoodleResult
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName::class)
class MoodleTest {
    private companion object {
        const val TAG = "MoodleTest"

        const val TEST_UNI_URL = "https://moodle.hainan-biuh.edu.cn"
        const val TEST_ILLEGAL_CONTENT = "乱七八糟"
        const val TEST_USERNAME = "chen.junhao.25"
        const val TEST_PASSWORD = "w09t3t0r1sTezC@md"

        inline fun <reified T> T.toJson(): String = Json.encodeToString(this)

        suspend fun assertFailsAndLog(tag: String, message: String, block: suspend () -> Unit) {
            val e = assertFails { block() }
            MoodleLog.d(tag, "$message -> $e")
        }

        fun testUnit(action: String, result: MoodleResult<Unit>) = when (result) {
            is MoodleResult.Success -> MoodleLog.i(TAG, "${action}成功")

            is MoodleResult.Failure -> MoodleLog.e(TAG, "${action}失败：${result.exception.stackTraceToString()}")
        }

        inline fun <reified T> test(action: String, result: MoodleResult<T>) = when (result) {
            is MoodleResult.Success -> MoodleLog.i(TAG, "${action}成功：${result.data.toJson()}")

            is MoodleResult.Failure -> MoodleLog.e(TAG, "${action}失败：${result.exception.stackTraceToString()}")
        }
    }

    val moodleFetcher = MoodleFetcher()

    // @Test
    fun test00_resParsing() = runBlocking {
        MoodleLog.i(TAG, "测试构建：MoodleCourseRes")
        assertEquals(114514, MoodleCourseRes(114514).data) // 测试正确构建

        MoodleLog.i(TAG, "测试解析器：MoodleCourseRes")
        assertFailsAndLog(TAG, "测试非法URL") { MoodleCourseRes.parse(TEST_ILLEGAL_CONTENT) }
        assertFailsAndLog(TAG, "测试不含有目标路径") { MoodleCourseRes.parse(TEST_UNI_URL) }
        assertFailsAndLog(TAG, "测试不含有目标参数") { MoodleCourseRes.parse("${TEST_UNI_URL}/course/view.php") }
        assertEquals(114514, MoodleCourseRes.parse("${TEST_UNI_URL}/course/view.php?id=114514").data)
    }

    @Test
    fun test01_login() = runBlocking {
        val action = "登录"

        /*MoodleLog.i(TAG, "测试$action，应失败：非法URL")
        (moodleFetcher.login(TEST_ILLEGAL_CONTENT, TEST_USERNAME, TEST_PASSWORD) as MoodleResult.Failure).exception.printStackTrace()

        MoodleLog.i(TAG, "测试$action，应失败：账号、密码错误")
        (moodleFetcher.login(TEST_UNI_URL, TEST_ILLEGAL_CONTENT, TEST_ILLEGAL_CONTENT) as MoodleResult.Failure).exception.printStackTrace()*/

        MoodleLog.i(TAG, "测试$action，应成功")
        testUnit(action, moodleFetcher.login(TEST_UNI_URL, TEST_USERNAME, TEST_PASSWORD))
    }

    // @Test
    fun test02_getGrades() = runBlocking {
        val action = "获取成绩列表"

        MoodleLog.i(TAG, "测试$action")

        test(action, moodleFetcher.getGrades())
    }

    // @Test
    fun test03_getCourses() = runBlocking {
        val action = "获取课程列表"

        MoodleLog.i(TAG, "测试$action")

        test(action, moodleFetcher.getCourses())
    }

    @Test
    fun test04_getCourse() = runBlocking {
        val action = "获取课程"

        MoodleLog.i(TAG, "测试$action")

        test("${action}4171", moodleFetcher.getCourseById(4171))

        // test("${action}4158", moodleFetcher.getCourseById(4158))

        // test("${action}4289", moodleFetcher.getCourseById(4289))
    }
}