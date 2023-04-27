package com.anuraagpotdaar.teacherconnect

data class TaskData(
    val taskName: String,
    val priority: String,
    val status: String,
    val originalPosition: Int
) {
    companion object {
        fun fromMap(map: Map<String, Any>, originalPosition: Int): TaskData {
            return TaskData(
                taskName = map["task"] as? String ?: "",
                priority = map["priority"] as? String ?: "",
                status = map["status"] as? String ?: "",
                originalPosition = originalPosition
            )
        }
    }
}
