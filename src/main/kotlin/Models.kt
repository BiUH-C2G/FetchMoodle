package lib.fetchmoodle

import kotlinx.serialization.Serializable

// CHECK：我没搞MoodleLink，你直接拿`url`解析Res先吧

@Serializable
data class MoodleCourseInfo(
    val id: Int,
    val name: String,
    val category: String,
    val url: String
)

@Serializable
data class MoodleCourse(
    val id: Int,
    val contextId: Int,
    val name: String,
    val category: String?, // CHECK：部分站点可能没有分类元素
    val sections: List<CourseSection>
)

interface SectionLike {
    val name: String
    val summary: String?
    val modules: List<CourseModule>
}

@Serializable
data class CourseSection(
    val id: Int,
    val number: Int,
    override val name: String,
    override val summary: String? = null,
    override val modules: List<CourseModule>
) : SectionLike

@Serializable
sealed class CourseModule {
    abstract val id: Int
    abstract val name: String
    abstract val url: String
    abstract val isVisible: Boolean
    abstract val availability: CourseModuleAvailability?

    // 具体的组件类型

    @Serializable
    data class Resource(
        override val id: Int,
        override val name: String,
        override val url: String,
        override val isVisible: Boolean,
        override val availability: CourseModuleAvailability?,
        val fileType: String?,
        val fileSize: String?,
        val uploadDate: String?,
        val description: String?
    ) : CourseModule()

    @Serializable
    data class Assignment(
        override val id: Int,
        override val name: String,
        override val url: String,
        override val isVisible: Boolean,
        override val availability: CourseModuleAvailability?,
        val description: String?,
        val openDate: String?,
        val dueDate: String?
    ) : CourseModule()

    @Serializable
    data class Quiz(
        override val id: Int,
        override val name: String,
        override val url: String,
        override val isVisible: Boolean,
        override val availability: CourseModuleAvailability?,
        val description: String?,
        val openDate: String?,
        val closeDate: String?
    ) : CourseModule()

    @Serializable
    data class Forum(
        override val id: Int,
        override val name: String,
        override val url: String,
        override val isVisible: Boolean,
        override val availability: CourseModuleAvailability? = null
    ) : CourseModule()

    @Serializable
    data class Folder(
        override val id: Int,
        override val name: String,
        override val url: String,
        override val isVisible: Boolean,
        override val availability: CourseModuleAvailability? = null
    ) : CourseModule()

    @Serializable
    data class Label(
        override val id: Int,
        override val name: String = "",
        override val url: String = "",
        override val isVisible: Boolean = true,
        override val availability: CourseModuleAvailability? = null,
        val contentHtml: String
    ) : CourseModule()

    @Serializable
    data class Attendance(
        override val id: Int,
        override val name: String,
        override val url: String,
        override val isVisible: Boolean,
        override val availability: CourseModuleAvailability? = null
    ) : CourseModule()

    @Serializable
    data class Unknown(
        override val id: Int,
        override val name: String,
        override val url: String,
        val modType: String,
        override val isVisible: Boolean,
        override val availability: CourseModuleAvailability? = null
    ) : CourseModule()

    @Serializable
    data class SubSection(
        override val id: Int,
        override val name: String,
        override val url: String,
        override val isVisible: Boolean,
        override val availability: CourseModuleAvailability?,
        override val summary: String? = null,
        override val modules: List<CourseModule> // 嵌套的模块列表
    ) : SectionLike, CourseModule()
}

@Serializable
data class CourseModuleAvailability(
    val isRestricted: Boolean,
    val description: String? = null
)

@Serializable
data class MoodleCourseGrade(
    val name: String,
    val url: String,
    val grade: String
)