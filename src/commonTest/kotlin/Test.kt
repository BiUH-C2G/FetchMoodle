import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json
import lib.fetchmoodle.BuildKonfig
import lib.fetchmoodle.MoodleCourseRes
import lib.fetchmoodle.MoodleFetcher
import lib.fetchmoodle.MoodleLoginFailureException
import lib.fetchmoodle.MoodleResult
import lib.fetchmoodle.ResParsingException

// 辅助函数：格式化 JSON
inline fun <reified T> T.toJson(): String = Json.encodeToString(this)

// 辅助函数：通用的 Result 校验逻辑
inline fun <reified T> MoodleResult<T>.shouldBeSuccess(action: String): T = when (this) {
    is MoodleResult.Success -> {
        println("$action 成功：${data.toJson()}")
        data
    }

    is MoodleResult.Failure -> throw AssertionError("$action 失败：${exception.message}", exception)
}

// TIPS：进行JVM测试或iOS测试
class MoodleTest : BehaviorSpec({
    // 配置：测试数据
    val realSite = BuildKonfig.MOODLE_TEST_URL.trim()
    val realUsername = BuildKonfig.MOODLE_TEST_USERNAME.trim()
    val realPassword = BuildKonfig.MOODLE_TEST_PASSWORD.trim()

    val illegalContent = "乱七八糟"

    val finalSite = realSite.ifBlank { "https://school.moodledemo.net" }
    val finalUsername = realUsername.ifBlank { "student" }
    val finalPassword = realPassword.ifBlank { "moodle25" }

    val hasRealCredentials = listOf(realSite, realUsername, realPassword).all { it.isNotBlank() }

    val moodleFetcher = MoodleFetcher()

    Given("解析 Moodle 课程 URL 时") {
        val validUrl = "$finalSite/course/view.php?id=114514"

        When("输入合法的 URL") {
            val res = MoodleCourseRes.parse(validUrl)
            Then("能正确解析出课程 ID") { res.data shouldBe 114514 }
        }

        When("输入非法的 URL") {
            Then("应该抛出解析异常") {
                shouldThrow<ResParsingException> { MoodleCourseRes.parse(illegalContent) }
                shouldThrow<ResParsingException> { MoodleCourseRes.parse(finalSite) }
            }
        }
    }

    Given("进行登录操作") {
        When("提供错误的 URL") {
            val result = moodleFetcher.login(illegalContent, finalUsername, finalPassword)
            Then("结果应该是失败，并包含登录异常") {
                result.shouldBeInstanceOf<MoodleResult.Failure>()
                result.exception.shouldBeInstanceOf<MoodleLoginFailureException>()
            }
        }

        println("在${if (hasRealCredentials) "真实" else "Demo"}站点上测试")

        When("提供正确的凭据") {
            val result = moodleFetcher.login(finalSite, finalUsername, finalPassword)

            Then("登录应该成功") { result.shouldBeSuccess("登录") }

            // 在登录成功的上下文内测试后续功能
            And("获取各类数据") {
                Then("可以获取成绩列表") { moodleFetcher.getGrades().shouldBeSuccess("获取成绩列表") }

                Then("可以获取课程列表") { moodleFetcher.getCourses().shouldBeSuccess("获取课程列表") }

                Then("可以根据 ID 获取特定课程") {
                    if (hasRealCredentials) moodleFetcher.getCourseById(4158).shouldBeSuccess("获取真实课程")
                    else moodleFetcher.getCourseById(51).shouldBeSuccess("获取Demo课程")
                }

                Then("可以获取近期项目") { moodleFetcher.getRecentItems().shouldBeSuccess("获取近期项目") }

                Then("可以获取时间线") { moodleFetcher.getTimeline().shouldBeSuccess("获取时间线") }

                Then("可以获取用户信息") { moodleFetcher.getUserProfile().shouldBeSuccess("获取用户信息") }
            }
        }
    }
})
