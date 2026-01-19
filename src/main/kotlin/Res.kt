package lib.fetchmoodle

import lib.fetchmoodle.UriUtils.getQueryParam
import java.net.URI

interface MoodleRes<RES_TYPE> {
    val type: String

    val data: RES_TYPE
}

interface MoodleResParser<RES_TYPE, RAW_TYPE> {
    fun parse(raw: RAW_TYPE): MoodleRes<RES_TYPE>
}

class ResParsingException(raw: Any, type: String, message: String?, cause: Exception? = null) : MoodleException("从\"$raw\"解析${type}失败${if (message != null) "：$message" else ""}", cause)

class MoodleCourseRes(override val data: Int) : MoodleRes<Int> {
    override val type = TYPE

    companion object : MoodleResParser<Int, String> {
        private const val TYPE = "course"

        override fun parse(raw: String): MoodleCourseRes = try {
            val uri = URI(raw)

            val path = uri.path ?: throw Exception("无效路径")

            if (!path.endsWith("/course/view.php")) throw Exception("${if (path.isNotEmpty()) "\"$path\"" else ""}非本资源路径")

            val id = uri.getQueryParam("id")?.toIntOrNull() ?: throw Exception("无有效ID")

            MoodleCourseRes(id)
        } catch (e: Exception) {
            throw ResParsingException(raw, TYPE, e.message)
        }
    }
}