package com.anuraagpotdaar.teacherconnect

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.anuraagpotdaar.teacherconnect.databinding.ActivityLoginBinding
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding: ActivityLoginBinding = ActivityLoginBinding.inflate(layoutInflater)

        setContentView(binding.root)

        binding.button.setOnClickListener {
            val userID = binding.etUsername.text.toString().trim()
            val pass = binding.etPass.text.toString().trim()

            if (pass == userID && userID.length > 4) {
                checkIfIdExists(userID, onSuccess = {
                    val intent = Intent(this, DashboardActivity::class.java)
                    startActivity(intent)
                    this.finish()
                }, onFailure = {
                    Toast.makeText(this, "Invalid ID", Toast.LENGTH_SHORT).show()
                    binding.etUsername.error = "Invalid ID"
                })
            }
        }
    }

    private fun checkIfIdExists(id: String, onSuccess: (String) -> Unit, onFailure: () -> Unit) {
        val firestore = FirebaseFirestore.getInstance()
        val collectionRef = firestore.collection("teachers")

        collectionRef.get().addOnSuccessListener { documents ->
            var matchedId: String? = null
            for (document in documents) {
                if (document.id.startsWith(id)) {
                    matchedId = document.id
                    break
                }
            }

            if (matchedId != null) {
                saveIdToSharedPreferences(matchedId)
                onSuccess(matchedId)
            } else {
                onFailure()
            }
        }.addOnFailureListener {
            onFailure()
        }
    }

    private fun saveIdToSharedPreferences(id: String) {
        SharedPreferencesUtil.getSavedIdFromSharedPreferences(this)
        SharedPreferencesUtil.saveIdToSharedPreferences(this, id)
    }
}