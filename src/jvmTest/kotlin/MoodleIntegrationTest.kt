package lib.fetchmoodle

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertIs
import kotlin.test.fail

@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MoodleIntegrationTest {
    private data class IntegrationConfig(
        val baseUrl: String,
        val username: String,
        val password: String
    )

    private val config: IntegrationConfig by lazy {
        IntegrationConfig(
            baseUrl = readConfig("moodle.baseUrl", "MOODLE_TEST_BASE_URL", "https://school.moodledemo.net"),
            username = readConfig("moodle.username", "MOODLE_TEST_USERNAME", "student"),
            password = readConfig("moodle.password", "MOODLE_TEST_PASSWORD", "moodle25")
        )
    }

    private val authedFetcher = MoodleFetcher()

    @BeforeAll
    fun setUp() = runBlocking {
        val enabled = System.getProperty("moodle.integration")?.equals("true", ignoreCase = true) == true
        assumeTrue(
            enabled,
            "Set -Dmoodle.integration=true to run integration tests. Optional overrides: " +
                    "-Dmoodle.baseUrl/-Dmoodle.username/-Dmoodle.password"
        )

        assertSuccess(
            authedFetcher.login(config.baseUrl, config.username, config.password),
            "登录测试账号"
        )
    }

    @Test
    fun login_invalidUrlFails(): Unit = runBlocking {
        val fetcher = MoodleFetcher()
        val result = fetcher.login("乱七八糟", config.username, config.password)
        assertFailure(result, "非法 URL 登录应失败")
    }


    @Test
    fun login_wrongCredentialsFail(): Unit = runBlocking {
        val fetcher = MoodleFetcher()
        val result = fetcher.login(config.baseUrl, "not_exists_user", "wrong_password")
        assertFailure(result, "错误账号密码应失败")
    }

    @Test
    fun getGrades_success(): Unit = runBlocking {
        assertSuccess(authedFetcher.getGrades(), "获取成绩列表")
    }

    @Test
    fun getCoursesAndCourseById_success(): Unit = runBlocking {
        val courses = assertSuccess(authedFetcher.getCourses(), "获取课程列表")
        assumeTrue(courses.isNotEmpty(), "测试账号没有课程，跳过课程详情测试")
        assertSuccess(authedFetcher.getCourseById(courses.first().id), "获取课程详情")
    }

    @Test
    fun getRecentItems_success(): Unit = runBlocking {
        assertSuccess(authedFetcher.getRecentItems(), "获取近期项目")
    }

    @Test
    fun getTimeline_success(): Unit = runBlocking {
        assertSuccess(authedFetcher.getTimeline(), "获取时间线")
    }

    @Test
    fun getUserProfile_success(): Unit = runBlocking {
        assertSuccess(authedFetcher.getUserProfile(), "获取用户数据")
    }

    private fun readConfig(propertyKey: String, envKey: String, default: String): String =
        System.getProperty(propertyKey) ?: System.getenv(envKey) ?: default

    private fun assertFailure(result: MoodleResult<*>, action: String): MoodleResult.Failure {
        val failure = assertIs<MoodleResult.Failure>(result, action)
        assertIs<MoodleLoginFailureException>(failure.exception)
        return failure
    }

    private fun <T> assertSuccess(result: MoodleResult<T>, action: String): T = when (result) {
        is MoodleResult.Success -> result.data
        is MoodleResult.Failure -> fail("$action 失败: ${result.exception.stackTraceToString()}")
    }
}
