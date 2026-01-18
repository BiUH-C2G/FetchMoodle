package lib.fetchmoodle

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

sealed class MoodleResult<out RESULT_TYPE> {
    data class Success<RESULT_TYPE>(val data: RESULT_TYPE) : MoodleResult<RESULT_TYPE>()
    data class Failure(val exception: Exception) : MoodleResult<Nothing>()
}

interface MoodleOperation<RESULT_TYPE> {
    suspend fun MoodleContext.execute(): MoodleResult<RESULT_TYPE>
}

abstract class MoodleQuery<RESULT_TYPE> : MoodleOperation<RESULT_TYPE> {
    private companion object {
        const val TAG = "MoodleQuery"
    }

    abstract val path: String

    open fun MoodleContext.configureRequest(builder: HttpRequestBuilder) {
        builder.injectMoodleSession()
    }

    abstract fun MoodleContext.parseDocument(document: Document): RESULT_TYPE

    override suspend fun MoodleContext.execute(): MoodleResult<RESULT_TYPE> = runCatching {
        val response = httpClient.get("${baseUrl}/$path") { configureRequest(this) }

        if (!response.status.isSuccess()) throw MoodleException("请求失败，状态码：${response.status.value}")

        val html = response.bodyAsText()
        Jsoup.parse(html)
    }.mapCatching { document ->
        parseDocument(document)
    }.fold({ MoodleResult.Success(it) }, { MoodleResult.Failure(MoodleException("请求执行失败：${it.message}", it)) })
}

class LoginOperation(private val username: String, private val password: String) : MoodleOperation<Unit> {
    private companion object {
        const val TAG = "LoginOperation"

        suspend fun extractLoginToken(response: HttpResponse): String {
            val doc = Jsoup.parse(response.bodyAsText())
            return doc.selectFirst("input[name=logintoken]")?.attr("value") ?: throw MoodleException("无法提取登录Token")
        }

        fun extractSesskey(text: String): String {
            val regex = "\"sesskey\":\"([^\"]+)\"".toRegex()
            val match = regex.find(text)
            return match?.groupValues?.get(1) ?: throw MoodleException("无法提取Sesskey")
        }

        fun extractMoodleSession(response: HttpResponse): String {
            val theCookie = response.setCookie().find { it.name == "MoodleSession" }
            return theCookie?.value ?: throw MoodleException("无法提取MoodleSession：有可能是账号或密码错误")
        }
    }

    override suspend fun MoodleContext.execute(): MoodleResult<Unit> {
        return try {
            val loginUrl = "$baseUrl/login/index.php"

            // 获取登录Token
            val firstResponse = httpClient.get(loginUrl)
            val token = extractLoginToken(firstResponse)
            moodleSession = extractMoodleSession(firstResponse)
            MoodleLog.i(TAG, "获取到登录Token与一阶段Moodle会话: $token，$moodleSession")

            // 提交表单并获取MoodleSession
            val formResponse = httpClient.submitForm(loginUrl, parameters {
                append("anchor", "")
                append("logintoken", token)
                append("username", username)
                append("password", password)
            }) { injectMoodleSession() }
            moodleSession = extractMoodleSession(formResponse)
            MoodleLog.i(TAG, "获取到最终Moodle会话: $moodleSession")

            val myResponse = httpClient.get("$baseUrl/my/") { injectMoodleSession() } // 模拟登录后重定向操作
            val myHtml = myResponse.bodyAsText()
            if (myHtml.contains("loginerrormessage")) return MoodleResult.Failure(MoodleException("登录失败：用户名或密码错误"))

            // 从myHtml提取 sesskey 供以后的 API 调用使用
            sesskey = extractSesskey(myHtml)
            MoodleLog.i(TAG, "获取到Sesskey: $sesskey")

            MoodleResult.Success(Unit)
        } catch (e: Exception) {
            // 清理会话数据
            sesskey = null
            moodleSession = null

            MoodleResult.Failure(MoodleException("登录时出错：${e.stackTraceToString()}"))
        }
    }
}

class GradesQuery : MoodleQuery<Map<String, String>>() { // TODO：应提取Res类型
    private companion object {
        const val TAG = "GradesQuery"
    }

    override val path: String = "grade/report/overview/index.php"

    override fun MoodleContext.parseDocument(document: Document): Map<String, String> {
        val validGradesMap = mutableMapOf<String, String>()

        // 1. 定位到具体的表格，ID 为 overview-grade
        val table = document.getElementById("overview-grade") ?: throw MoodleException("无法找到成绩表单，可能尚未登录或页面结构已变")

        // 2. 获取所有的行，并过滤掉 class 包含 emptyrow 的行
        val rows = table.select("tbody tr:not(.emptyrow)")

        for (row in rows) {
            val courseNameCell = row.selectFirst("td.c0")
            val courseName = courseNameCell?.text()?.trim() ?: continue // 跳过无法提取课程名的行

            // 4. 提取分值 (c1 单元格)
            val gradeCell = row.selectFirst("td.c1")
            val gradeText = gradeCell?.text()?.trim().run { if (isNullOrEmpty() || this == "-") "无数据" else this }

            validGradesMap[courseName] = gradeText
        }

        return validGradesMap
    }
}