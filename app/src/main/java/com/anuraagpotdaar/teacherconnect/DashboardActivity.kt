package com.anuraagpotdaar.teacherconnect

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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

        documentRef.get()
            .addOnSuccessListener { document ->
                if (document != null) {
                    val name = document.getString("Personal_details.f_name")
                    val institute = document.getString("Prev_postings.institute_name_1")
                    val behavior = document.getString("Prev_postings.behavior")
                    val availableLeaves = document.getLong("Prev_postings.availableLeaves")?.toInt()

                    binding.tvName.text = name
                    binding.tvInstitute.text = institute
                    binding.tvBehavior.text = behavior
                    binding.tvAvailableLeaves.text = availableLeaves.toString()

                } else {
                    Toast.makeText(this, "No such document", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to fetch data", Toast.LENGTH_SHORT).show()
            }
    }
}