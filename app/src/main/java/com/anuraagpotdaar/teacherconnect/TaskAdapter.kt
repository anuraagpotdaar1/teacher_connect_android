package com.anuraagpotdaar.teacherconnect

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class TaskAdapter(private val tasks: List<TaskData>, private val context: Context) :
    RecyclerView.Adapter<TaskAdapter.TaskDataViewHolder>() {
    private val teacherId = SharedPreferencesUtil.getSavedIdFromSharedPreferences(context)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskDataViewHolder {
        val itemView =
            LayoutInflater.from(parent.context).inflate(R.layout.item_my_tasks, parent, false)
        return TaskDataViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: TaskDataViewHolder, position: Int) {
        val task = tasks[position]
        holder.bind(task, teacherId!!)
    }

    override fun getItemCount() = tasks.size

    class TaskDataViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTaskName: TextView = itemView.findViewById(R.id.tvTaskName)
        private val tvDescription: TextView = itemView.findViewById(R.id.tvPriority)
        private val btnMarkDone: Button = itemView.findViewById(R.id.btnMarkDone)

        fun bind(task: TaskData, teacherId: String) {
            tvTaskName.text = task.taskName
            tvDescription.text = task.priority

            btnMarkDone.setOnClickListener {
                updateStatusToCompleted(teacherId, task.originalPosition)
            }
        }


        private fun updateStatusToCompleted(teacherId: String, position: Int) {
            Log.d(
                "TaskAdapter",
                "updateStatusToCompleted() called with teacherId: $teacherId, position: $position"
            )

            val firestore = FirebaseFirestore.getInstance()
            val teacherRef = firestore.collection("teachers").document(teacherId)

            teacherRef.get().addOnSuccessListener { document ->
                if (document != null) {
                    val tasksList = document.get("Tasks") as? List<Map<String, Any>> ?: emptyList()
                    Log.d("TaskAdapter", "Tasks list: $tasksList")

                    val oldTask = tasksList[position]
                    val updatedTask = oldTask.toMutableMap()
                    updatedTask["status"] = "Completed"

                    teacherRef.update(
                        "Tasks",
                        FieldValue.arrayRemove(oldTask),
                        "Tasks",
                        FieldValue.arrayUnion(updatedTask)
                    ).addOnSuccessListener {
                            Log.d("TaskAdapter", "Task status updated successfully")
                        }.addOnFailureListener { exception ->
                            Log.e("TaskAdapter", "Failed to update task status", exception)
                        }
                } else {
                    Log.w("TaskAdapter", "Document not found")
                }
            }.addOnFailureListener { exception ->
                Log.e("TaskAdapter", "Failed to get document", exception)
            }
        }
    }
}
