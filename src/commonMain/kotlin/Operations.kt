package lib.fetchmoodle

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import lib.fetchmoodle.JsoupUtils.allText
import kotlin.time.Clock

sealed class MoodleResult<out RESULT_TYPE> {
    data class Success<RESULT_TYPE>(val data: RESULT_TYPE) : MoodleResult<RESULT_TYPE>()
    data class Failure(val exception: Exception) : MoodleResult<Nothing>()
}

interface MoodleOperation<RESULT_TYPE> {
    suspend fun MoodleContext.execute(): MoodleResult<RESULT_TYPE>
}

open class MoodleOperationException(message: String, cause: Throwable? = null) : MoodleException(message, cause)

abstract class MoodleHtmlQueryOperation<RESULT_TYPE> : MoodleOperation<RESULT_TYPE> {
    private companion object Companion {
        const val TAG = "MoodleHttpQuery"
    }

    abstract val path: String

    open fun MoodleContext.configureRequest(builder: HttpRequestBuilder) {
        builder.injectMoodleSession()
    }

    abstract fun MoodleContext.parseDocument(document: Document): RESULT_TYPE

    override suspend fun MoodleContext.execute(): MoodleResult<RESULT_TYPE> = runCatching {
        val fullUrl = "${baseUrl}/$path"

        MoodleLog.i(TAG, "行将访问：$fullUrl")

        val response = httpClient.get(fullUrl) { configureRequest(this) }

        if (!response.status.isSuccess()) throw MoodleHtmlQueryException("请求失败，状态码：${response.status.value}")

        val html = response.bodyAsText()
        Ksoup.parse(html)
    }.mapCatching { document ->
        parseDocument(document)
    }.fold({ MoodleResult.Success(it) }, { MoodleResult.Failure(MoodleHtmlQueryException("执行失败：${it.message}", it)) })
}

class MoodleHtmlQueryException(message: String, cause: Throwable? = null) : MoodleOperationException("Html查询报错：$message", cause)

abstract class MoodleAjaxQueryOperation<RESULT_TYPE> : MoodleOperation<RESULT_TYPE> {
    companion object Companion {
        private const val TAG = "MoodleAjaxQuery"

        fun RequestMethod.toKtorMethod(): HttpMethod = when (this) {
            RequestMethod.GET -> HttpMethod.Get
            RequestMethod.POST -> HttpMethod.Post
        }
    }

    enum class RequestMethod { GET, POST }

    abstract val requestMethod: RequestMethod

    abstract val info: String

    abstract val body: String?

    open fun MoodleContext.configureRequest(builder: HttpRequestBuilder) {
        builder.injectMoodleSession()
        builder.injectSesskey()
    }

    abstract fun MoodleContext.parseJson(json: String): RESULT_TYPE

    override suspend fun MoodleContext.execute(): MoodleResult<RESULT_TYPE> = runCatching {
        // 构建带 sesskey 和 info 的 URL
        val ajaxUrl = "$baseUrl/lib/ajax/service.php"

        MoodleLog.i(TAG, "行将请求Ajax：$info")

        val response = httpClient.request(ajaxUrl) {
            method = requestMethod.toKtorMethod()

            // 设置 URL 参数
            url.parameters.append("info", info)

            // 注入 Cookie 等 Session 信息
            configureRequest(this)

            this@MoodleAjaxQueryOperation.body?.let {
                setBody(it)
                contentType(ContentType.Application.Json)
            }
        }

        if (!response.status.isSuccess()) throw MoodleAjaxQueryException("请求失败，状态码：${response.status.value}")

        response.bodyAsText()
    }.mapCatching { jsonString ->
        parseJson(jsonString)
    }.fold({ MoodleResult.Success(it) }, { MoodleResult.Failure(MoodleAjaxQueryException("执行\"$info\"失败：${it.message}", it)) })
}

class MoodleAjaxQueryException(message: String, cause: Throwable? = null) : MoodleOperationException("Ajax查询报错：$message", cause)

sealed class LoginFailure {
    data object InvalidCredentials : LoginFailure()
    data object Network : LoginFailure()
    data class Unknown(val message: String?) : LoginFailure()
}

class MoodleLoginFailureException(val failure: LoginFailure, cause: Throwable? = null) : MoodleOperationException("登录失败：$failure", cause)

// CHECK：关于省流，如果最后改my为profile，可以一并获取用户信息，但这样就做了profile op的职责了，本op的职责也不干净了
class LoginOperation(private val baseUrl: String, private val username: String, private val password: String) : MoodleOperation<Unit> {
    private companion object {
        const val TAG = "LoginOperation"

        fun extractLoginToken(html: String): String {
            val doc = Ksoup.parse(html)
            return doc.selectFirst("input[name=logintoken]")?.attr("value") ?: throw MoodleException("无法提取登录Token")
        }

        fun extractSesskey(html: String): String {
            val regex = "\"sesskey\":\"([^\"]+)\"".toRegex()
            val match = regex.find(html)
            return match?.groupValues?.get(1) ?: throw MoodleException("无法提取Sesskey")
        }

        fun extractMoodleSessionOrNull(response: HttpResponse): String? {
            val theCookie = response.setCookie().find { it.name == "MoodleSession" }
            return theCookie?.value
        }

        fun classifyLoginFailure(error: Throwable): LoginFailure = when {
            error.isNetworkError() -> LoginFailure.Network
            else -> LoginFailure.Unknown(error.message)
        }

        fun Throwable.isNetworkError(): Boolean {
            var current: Throwable? = this
            while (current != null) {
                val className = current::class.qualifiedName.orEmpty()

                if (
                    className.endsWith("IOException") ||
                    className.endsWith("ConnectException") ||
                    className.endsWith("TimeoutException") ||
                    className.endsWith("UnresolvedAddressException")
                ) return true

                current = current.cause
            }
            return false
        }
    }

    override suspend fun MoodleContext.execute(): MoodleResult<Unit> {
        return try {
            baseUrl = this@LoginOperation.baseUrl

            val loginUrl = "$baseUrl/login/index.php"

            val firstResponse = httpClient.get(loginUrl)
            if (!firstResponse.status.isSuccess()) throw MoodleLoginFailureException(LoginFailure.Unknown("加载登录页失败，状态码：${firstResponse.status.value}"))
            val firstHtml = firstResponse.bodyAsText()
            val token = extractLoginToken(firstHtml)
            moodleSession = extractMoodleSessionOrNull(firstResponse) ?: throw MoodleLoginFailureException(LoginFailure.Unknown("登录页未返回MoodleSession"))
            MoodleLog.i(TAG, "获取到登录Token与一阶段Moodle会话: $token，$moodleSession")

            val formResponse = httpClient.submitForm(loginUrl, parameters {
                append("anchor", "")
                append("logintoken", token)
                append("username", username)
                append("password", password)
            }) { injectMoodleSession() }
            moodleSession = extractMoodleSessionOrNull(formResponse) ?: throw MoodleLoginFailureException(LoginFailure.InvalidCredentials)
            MoodleLog.i(TAG, "获取到最终Moodle会话: $moodleSession")

            // 模拟登录后重定向操作，以获取Sesskey
            val myResponse = httpClient.get("$baseUrl/my/") { injectMoodleSession() }
            if (!myResponse.status.isSuccess()) throw MoodleLoginFailureException(LoginFailure.Unknown("访问我的主页失败，状态码：${myResponse.status.value}"))
            val myHtml = myResponse.bodyAsText()
            sesskey = extractSesskey(myHtml)
            MoodleLog.i(TAG, "获取到Sesskey: $sesskey")

            MoodleResult.Success(Unit)
        } catch (e: Exception) {
            // 清理会话数据
            cleanSessionData()

            val moodleLoginFailureException = e as? MoodleLoginFailureException ?: MoodleLoginFailureException(classifyLoginFailure(e), e)
            MoodleResult.Failure(moodleLoginFailureException)
        }
    }
}

class GradesQueryOperation : MoodleHtmlQueryOperation<List<MoodleCourseGrade>>() { // TODO：应提取Res类型
    private companion object Companion {
        const val TAG = "GradesQuery"
    }

    override val path = "grade/report/overview/index.php"

    override fun MoodleContext.parseDocument(document: Document): List<MoodleCourseGrade> {
        val validGrades = mutableListOf<MoodleCourseGrade>()

        val table = document.getElementById("overview-grade") ?: throw MoodleException("无法找到成绩表单，可能尚未登录或页面结构已变")

        val rows = table.select("tbody tr:not(.emptyrow)")

        for (row in rows) {
            val courseNameElement = row.selectFirst("td.c0")?.firstElementChild()
            val courseName = courseNameElement?.text()?.trim() ?: continue
            val courseLink = courseNameElement.attr("href")

            val gradeCell = row.selectFirst("td.c1")
            val gradeText = gradeCell?.text()?.trim().run { if (isNullOrEmpty() || this == "-") "无数据" else this }

            validGrades.add(MoodleCourseGrade(courseName, courseLink, gradeText))
        }

        return validGrades
    }
}

// TIPS：不用DTO是为了简化代码量
class CoursesQueryOperation : MoodleAjaxQueryOperation<List<MoodleCourseInfo>>() {
    override val requestMethod = RequestMethod.POST

    override val info = "core_course_get_enrolled_courses_by_timeline_classification"

    // 构造请求 Body
    override val body: String =
        """[{"index":0,"methodname":"core_course_get_enrolled_courses_by_timeline_classification","args":{"offset":0,"limit":0,"classification":"all","sort":"fullname","customfieldname":"","customfieldvalue":"","requiredfields":["id","fullname","shortname","showcoursecategory","showshortname","visible","enddate"]}}]"""

    override fun MoodleContext.parseJson(json: String): List<MoodleCourseInfo> {
        val jsonElement = Json.parseToJsonElement(json)

        val rapper = jsonElement.jsonArray.firstOrNull()?.jsonObject ?: throw MoodleAjaxQueryException("Moodle返回了空数据")

        if (rapper["error"]?.jsonPrimitive?.boolean == true) throw MoodleAjaxQueryException("Moodle犯错了：${rapper["exception"]}")

        // 提取 courses 数组
        val coursesArray = rapper["data"]?.jsonObject?.get("courses")?.jsonArray ?: return emptyList()

        return coursesArray.map { item ->
            val obj = item.jsonObject
            MoodleCourseInfo(obj["id"]?.jsonPrimitive?.int ?: 0, obj["fullname"]?.jsonPrimitive?.content ?: "未知课程名称", obj["coursecategory"]?.jsonPrimitive?.content ?: "未知分类", obj["viewurl"]?.jsonPrimitive?.content ?: "")
        }
    }
}

class RecentItemsQueryOperation() : MoodleAjaxQueryOperation<List<MoodleRecentItem>>() {
    override val requestMethod = RequestMethod.POST

    override val info = "block_recentlyaccesseditems_get_recent_items"

    // 动态构造请求 Body
    override val body: String = """[{"index":0,"methodname":"block_recentlyaccesseditems_get_recent_items","args":{"limit":0}}]"""

    override fun MoodleContext.parseJson(json: String): List<MoodleRecentItem> {
        val jsonElement = Json.parseToJsonElement(json)

        val wrapper = jsonElement.jsonArray.firstOrNull()?.jsonObject ?: throw MoodleAjaxQueryException("Moodle返回了空数据")

        if (wrapper["error"]?.jsonPrimitive?.boolean == true) throw MoodleAjaxQueryException("Moodle报错：${wrapper["exception"]}")

        val dataArray = wrapper["data"]?.jsonArray ?: return emptyList()

        return dataArray.map { item ->
            val obj = item.jsonObject

            MoodleRecentItem(
                obj["id"]?.jsonPrimitive?.int ?: 0,
                obj["modname"]?.jsonPrimitive?.content ?: "",
                obj["name"]?.jsonPrimitive?.content ?: "未知项目",
                obj["courseid"]?.jsonPrimitive?.int ?: 0,
                obj["coursename"]?.jsonPrimitive?.content ?: "未知课程",
                obj["cmid"]?.jsonPrimitive?.int ?: 0,
                obj["timeaccess"]?.jsonPrimitive?.long ?: 0L,
                obj["viewurl"]?.jsonPrimitive?.content ?: "",
                obj["icon"]?.jsonPrimitive?.content ?: ""
            )
        }
    }
}

class CourseQueryOperation(val courseRes: MoodleCourseRes) : MoodleHtmlQueryOperation<MoodleCourse>() {
    private companion object Companion {
        const val TAG = "CourseQuery"

        val FILE_SIZE_REGEX = Regex("(\\d+(\\.\\d+)?\\s*(KB|MB|GB))")
        val UPLOAD_DATE_REGEX = Regex("Uploaded\\s+(.*)")
        val CONTEXT_ID_REGEX = Regex("context-(\\d+)")

        fun Element.extractDates(openKey: String, closeKey: String): Pair<String?, String?> {
            val datesDivs = this.select(".activity-dates div")
            val open = datesDivs.find { it.text().contains(openKey) }?.ownText()?.trim()
            val close = datesDivs.find { it.text().contains(closeKey) }?.ownText()?.trim()
            return open to close
        }

        fun parseModuleList(parent: Element?): List<CourseModule> = parent?.selectFirst("ul[data-for='cmlist']")
            ?.children()
            ?.filter { it.hasClass("activity") }
            ?.mapNotNull { parseModule(it) } ?: emptyList()

        fun parseModule(el: Element): CourseModule? {
            val modId = el.attr("data-id").toIntOrNull() ?: return null
            val modType = el.classNames().find { it.startsWith("modtype_") }?.removePrefix("modtype_") ?: "unknown"

            val nameEl = el.selectFirst(".instancename")?.clone() // clone 保护原文档
            nameEl?.select(".accesshide")?.remove()
            val name = nameEl?.text()?.trim() ?: ""
            val url = el.selectFirst(".aalink, .stretched-link")?.attr("abs:href") ?: ""
            val isVisible = !el.hasClass("dimmed")
            val availability = el.selectFirst(".availabilityinfo")?.text()?.let { CourseModuleAvailability(true, it) }

            // TODO：更多类型；是不是所有玩意都可以有Description？
            return when (modType) {
                "resource" -> {
                    val details = el.selectFirst(".resourcelinkdetails")?.text() ?: ""
                    CourseModule.Resource(
                        modId, name, url, isVisible, availability,
                        el.selectFirst(".activitybadge")?.text(),
                        FILE_SIZE_REGEX.find(details)?.value,
                        UPLOAD_DATE_REGEX.find(details)?.groupValues?.get(1),
                        el.selectFirst(".activity-description")?.allText
                    )
                }

                "quiz", "assign" -> {
                    val (open, close) = el.extractDates("Opened", if (modType == "quiz") "Closed" else "Due")
                    val description = el.selectFirst(".activity-description")?.allText

                    if (modType == "quiz") CourseModule.Quiz(modId, name, url, isVisible, availability, description, open, close)
                    else CourseModule.Assignment(modId, name, url, isVisible, availability, description, open, close)
                }

                "subsection" -> {
                    val delegated = el.selectFirst(".delegated-section")

                    CourseModule.SubSection(
                        modId,
                        delegated?.attr("data-sectionname") ?: el.attr("data-activityname").ifBlank { "Unknown SubSection" },
                        url, isVisible, availability,
                        delegated?.selectFirst(".summarytext")?.run { if (hasText()) allText else null },
                        parseModuleList(delegated) // 复用列表解析
                    )
                }

                "label" -> CourseModule.Label(modId, name, url, isVisible, availability, el.select(".activity-altcontent").html())

                "forum" -> CourseModule.Forum(modId, name, url, isVisible, availability)

                "attendance" -> CourseModule.Attendance(modId, name, url, isVisible, availability)

                "folder" -> CourseModule.Folder(modId, name, url, isVisible, availability)

                else -> CourseModule.Unknown(modId, name, url, modType, isVisible, availability)
            }
        }
    }

    override val path: String = "course/view.php?id=${courseRes.data}"

    override fun MoodleContext.parseDocument(document: Document): MoodleCourse {
        val courseName = document.selectFirst(".page-header-headings h1")?.text() ?: document.title().substringBefore("|").trim()

        val breadcrumb = document.selectFirst(".breadcrumb-item a")?.text()

        // section的父可能老变，cnm moodle
        val sections = document.select("ul > li.section").map { sectionEl ->
            CourseSection(
                sectionEl.attr("data-sectionid").toIntOrNull() ?: 0,
                sectionEl.attr("data-number").toIntOrNull() ?: 0, // CHECK：这个数字有何意味
                sectionEl.selectFirst(".sectionname")?.text()?.trim() ?: "General",
                sectionEl.selectFirst(".summarytext")?.run { if (hasText()) allText else null },
                parseModuleList(sectionEl)
            )
        }

        val contextId = CONTEXT_ID_REGEX.find(document.body().className())?.groupValues?.get(1)?.toIntOrNull() ?: 0

        return MoodleCourse(courseRes.data, contextId, courseName, breadcrumb, sections)
    }
}

class TimelineQueryOperation(
    private val fromTimestamp: Long = (Clock.System.now().toEpochMilliseconds() / 1000) - 86400 * 7 // 默认从一周前开始拉取，包含刚过期的；为了数据清晰，不可不有
) : MoodleAjaxQueryOperation<List<MoodleTimelineEvent>>() {
    override
    val requestMethod = RequestMethod.POST

    override
    val info = "core_calendar_get_action_events_by_timesort"

    // TIPS：这个API阴间，limit最大只能50
    override
    val body: String = """[{"index":0,"methodname":"core_calendar_get_action_events_by_timesort","args":{"limitnum":50,"timesortfrom":$fromTimestamp,"limittononsuspendedevents":true}}]"""

    override fun MoodleContext.parseJson(json: String): List<MoodleTimelineEvent> {
        val jsonElement = Json.parseToJsonElement(json)

        val wrapper = jsonElement.jsonArray.firstOrNull()?.jsonObject ?: throw MoodleAjaxQueryException("Moodle返回了空数据")

        if (wrapper["error"]?.jsonPrimitive?.boolean == true) throw MoodleAjaxQueryException("Moodle报错：${wrapper["exception"]}")

        // 提取 events 数组
        val eventsArray = wrapper["data"]?.jsonObject?.get("events")?.jsonArray ?: return emptyList()

        return eventsArray.map { item ->
            val obj = item.jsonObject
            val courseObj = obj["course"]?.jsonObject
            val actionObj = obj["action"]?.jsonObject
            val iconObj = obj["icon"]?.jsonObject

            MoodleTimelineEvent(
                obj["id"]?.jsonPrimitive?.int ?: 0,
                obj["name"]?.jsonPrimitive?.content ?: "未知任务",
                obj["activityname"]?.jsonPrimitive?.content ?: "",
                obj["component"]?.jsonPrimitive?.content?.takeIf { it.length > 4 }?.substring(5) ?: "",
                obj["description"]?.jsonPrimitive?.content ?: "",
                obj["timesort"]?.jsonPrimitive?.long ?: 0L,
                obj["overdue"]?.jsonPrimitive?.boolean ?: false,
                courseObj?.get("id")?.jsonPrimitive?.int ?: 0,
                courseObj?.get("fullname")?.jsonPrimitive?.content ?: "未知课程",
                iconObj?.get("iconurl")?.jsonPrimitive?.content ?: "",
                actionObj?.get("name")?.jsonPrimitive?.content ?: "",
                actionObj?.get("url")?.jsonPrimitive?.content ?: obj["viewurl"]?.jsonPrimitive?.content ?: ""
            )
        }
    }
}

class UserProfileQueryOperation : MoodleHtmlQueryOperation<MoodleUserProfile>() {
    override val path = "user/profile.php"

    override fun MoodleContext.parseDocument(document: Document): MoodleUserProfile {
        val name = document.selectFirst("h1.h2.mb-0")?.text() ?: throw MoodleHtmlQueryException("获取不到名字，页面结构可能变化")

        val picElements = document.select("img.userpicture")
        if (picElements.isEmpty()) return MoodleUserProfile(name, null)

        val picUrl = picElements.last()?.attr("src") ?: throw MoodleHtmlQueryException("有图片却获取不到url，页面结构可能变化")

        return MoodleUserProfile(name, picUrl)
    }
}
