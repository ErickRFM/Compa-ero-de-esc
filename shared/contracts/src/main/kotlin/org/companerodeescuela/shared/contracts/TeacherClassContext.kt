package org.companerodeescuela.shared.contracts

/** Navigation context only; each destination must resolve it against its authorized data. */
data class TeacherClassContext(
    val classroomId: String,
    val subjectName: String,
    val groupName: String?,
) : java.io.Serializable {
    fun matches(subject: String, group: String): Boolean =
        subjectName.trim().equals(subject.trim(), ignoreCase = true) &&
            !groupName.isNullOrBlank() && groupName.trim().equals(group.trim(), ignoreCase = true)

    companion object {
        fun from(classroom: ClassroomSummary) = TeacherClassContext(classroom.id, classroom.name, classroom.groupName)
        const val STATE_KEY = "teacherClassContext"
    }
}
