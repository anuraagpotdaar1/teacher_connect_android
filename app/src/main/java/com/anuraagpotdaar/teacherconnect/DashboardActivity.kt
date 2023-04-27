package com.anuraagpotdaar.teacherconnect

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.anuraagpotdaar.teacherconnect.databinding.ActivityDashboardBinding
import com.google.firebase.firestore.FirebaseFirestore

class DashboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDashboardBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)

        setContentView(binding.root)

        val matchedId = SharedPreferencesUtil.getSavedIdFromSharedPreferences(this)
        if (matchedId != null) {
            fetchData(matchedId)
        } else {
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_LONG).show()
            val intent = Intent(this, LoginActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
        }

        binding.btnNewReq.setOnClickListener {
            val intent = Intent(this, NewRequestActivity::class.java)
            startActivity(intent)
        }
    }

    private fun fetchData(id: String) {
        val firestore = FirebaseFirestore.getInstance()
        val collectionRef = firestore.collection("teachers")
        val documentRef = collectionRef.document(id)

        documentRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Toast.makeText(this, "Failed to fetch data", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val name = snapshot.getString("Personal_details.f_name")
                val institute = snapshot.getString("Prev_postings.institute_name_1")
                val behavior = snapshot.getString("Prev_postings.behavior")
                val availableLeaves = snapshot.getLong("Prev_postings.availableLeaves")?.toInt()

                binding.tvName.text = name
                binding.tvInstitute.text = institute
                binding.tvBehavior.text = behavior
                binding.tvAvailableLeaves.text = availableLeaves.toString()

                val tasksList = snapshot.get("Tasks") as? List<Map<String, Any>> ?: emptyList()
                val incompleteTasks = tasksList.mapIndexedNotNull { index, taskData ->
                    val task = TaskData.fromMap(taskData, index)
                    if (task.status == "Incomplete") {
                        task
                    } else {
                        null
                    }
                }

                binding.recyclerView.layoutManager = LinearLayoutManager(this)
                val adapter = TaskAdapter(incompleteTasks, this)
                binding.recyclerView.adapter = adapter

            } else {
                Toast.makeText(this, "No such document", Toast.LENGTH_SHORT).show()
            }
        }
    }

}