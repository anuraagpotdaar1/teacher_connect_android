package com.anuraagpotdaar.teacherconnect

import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.anuraagpotdaar.teacherconnect.databinding.ActivityNewRequestBinding
import com.google.firebase.firestore.FirebaseFirestore

class NewRequestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding: ActivityNewRequestBinding = ActivityNewRequestBinding.inflate(layoutInflater)

        setContentView(binding.root)

        val matchedId = SharedPreferencesUtil.getSavedIdFromSharedPreferences(this)
        val profileName = intent.getStringExtra("Username")

        val reqTypeList = resources.getStringArray(R.array.request_types)
        val arrayAdapter = ArrayAdapter(this, R.layout.item_request_type, reqTypeList)
        binding.etReqType.setAdapter(arrayAdapter)

        binding.btnSubmitReq.setOnClickListener {
            val reqType = binding.etReqType.text.toString().trim()
            val req = binding.etReq.text.toString().trim()

            if (reqType.isNotEmpty() && req.isNotEmpty()) {
                uploadDataToFirestore(reqType, req, matchedId, profileName)
            } else {
                Toast.makeText(this, "Please enter data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun uploadDataToFirestore(
        reqType: String, req: String, matchedId: String?, profileName: String?
    ) {
        val firestore = FirebaseFirestore.getInstance()
        val collectionRef = firestore.collection("requests")

        val dataMap = hashMapOf(
            "request" to req,
            "reqType" to reqType,
            "status" to "Active",
            "by" to profileName,
            "useID" to matchedId,
        )

        collectionRef.add(dataMap).addOnSuccessListener {
                Toast.makeText(this, "Data uploaded successfully", Toast.LENGTH_SHORT).show()
                finish()
            }.addOnFailureListener { exception ->
                Toast.makeText(this, "Failed to upload data", Toast.LENGTH_SHORT).show()
                Log.e("UploadActivity", "Failed to upload data", exception)
            }
    }
}