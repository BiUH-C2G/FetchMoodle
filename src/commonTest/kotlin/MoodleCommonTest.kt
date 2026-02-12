package lib.fetchmoodle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MoodleCommonTest {
    @Test
    fun courseRes_parseValidUrl() {
        val res = MoodleCourseRes.parse("https://school.moodledemo.net/course/view.php?id=114514")
        assertEquals(114514, res.data)
    }

    @Test
    fun courseRes_parseInvalidUrl() {
        assertFailsWith<ResParsingException> { MoodleCourseRes.parse("乱七八糟") }
        assertFailsWith<ResParsingException> { MoodleCourseRes.parse("https://school.moodledemo.net") }
        assertFailsWith<ResParsingException> { MoodleCourseRes.parse("https://school.moodledemo.net/course/view.php") }
    }

    @Test
    fun recentItem_iconUrlExtraction() {
        val withIcon = MoodleRecentItem(
            id = 1,
            type = "resource",
            name = "item",
            courseId = 1,
            courseName = "course",
            courseModuleId = 1,
            timeAccess = 0L,
            viewUrl = "https://example.com",
            rawIconHtml = "<div><img src=\"https://example.com/icon.png\"/></div>"
        )
        assertEquals("https://example.com/icon.png", withIcon.iconUrl)

        val withoutIcon = withIcon.copy(rawIconHtml = "")
        assertNull(withoutIcon.iconUrl)
    }
}
