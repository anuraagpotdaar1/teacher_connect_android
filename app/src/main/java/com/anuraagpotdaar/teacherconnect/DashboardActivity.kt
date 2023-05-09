package com.anuraagpotdaar.teacherconnect

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.anuraagpotdaar.teacherconnect.databinding.ActivityDashboardBinding
import com.anuraagpotdaar.teacherconnect.databinding.CardItemBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


class DashboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDashboardBinding
    private lateinit var name: String

    private lateinit var reprimand: String
    private lateinit var matchedId: String

    data class CardItem(val title: String, val description: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)

        setContentView(binding.root)

        matchedId = SharedPreferencesUtil.getSavedIdFromSharedPreferences(this).toString()
        fetchData(matchedId)

        binding.btnNewReq.setOnClickListener {
            val intent = Intent(this, NewRequestActivity::class.java)
            intent.putExtra("Username", name)
            startActivity(intent)
        }

        binding.btnAttendance.setOnClickListener {
            if (isTimeWithinRange(15, 16)) {
                if (checkPermissions()) {
                    checkAttendance(matchedId)
                } else {
                    requestPermissions()
                }
            } else {
                showTimeRangeErrorDialog("Sorry, the attendance window is not available at this particular time. Try in institute specified time.")
            }
        }
        startAutoScroll(binding)
    }

    private val PERMISSION_REQUEST_CODE = 100

    private fun checkPermissions(): Boolean {
        val cameraPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        val locationPermission =
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)

        return cameraPermission == PackageManager.PERMISSION_GRANTED && locationPermission == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION),
            PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                checkAttendance(matchedId)
            } else {
                Toast.makeText(
                    this,
                    "Camera and location permissions are required to mark attendance",
                    Toast.LENGTH_LONG
                ).show()

            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }


    private fun isTimeWithinRange(startHour: Int, endHour: Int): Boolean {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

        return currentHour in startHour until endHour
    }

    private fun showTimeRangeErrorDialog(msg: String) {
        MaterialAlertDialogBuilder(this).setMessage(msg).setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
        }.setNegativeButton("Test functionality") { dialog, _ ->
            if (checkPermissions()) {
                val intent = Intent(this, AttendanceActivity::class.java)
                intent.putExtra("Username", name)
                startActivity(intent)
                dialog.dismiss()
            } else {
                requestPermissions()
            }
        }.show()
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
                name = snapshot.getString("Personal_details.f_name").toString()
                val institute = snapshot.getString("Prev_postings.institute_name_1")
                val behavior = snapshot.getString("Prev_postings.behaviour")
                val availableLeaves = snapshot.getString("Prev_postings.availableLeaves")

                val reprimandsList =
                    snapshot.get("Reprimands") as? List<Map<String, Any>> ?: emptyList()

                var latestTimestamp: Date? = null
                var latestRemark: String? = null

                for (reprimand in reprimandsList) {
                    val timestampStr = reprimand["timestamp"] as? String
                    val timestampFormat =
                        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                    val timestamp = if (timestampStr != null) {
                        try {
                            timestampFormat.parse(timestampStr)
                        } catch (e: ParseException) {
                            null
                        }
                    } else {
                        null
                    }

                    val remark = reprimand["remark"] as? String

                    if (timestamp != null && (latestTimestamp == null || timestamp > latestTimestamp)) {
                        latestTimestamp = timestamp
                        latestRemark = remark
                    }
                }

                reprimand = latestRemark ?: "No remarks found"

                updateCardItems()

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

    private fun checkAttendance(id: String) {
        val firestore = FirebaseFirestore.getInstance()
        val collectionRef = firestore.collection("teachers")
        val documentRef = collectionRef.document(id)

        documentRef.get().addOnSuccessListener { snapshot ->
            if (snapshot != null && snapshot.exists()) {
                val attendance = snapshot.get("attendance") as? Map<String, Any> ?: emptyMap()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val todayDate = dateFormat.format(Date())

                if (attendance.containsKey(todayDate)) {
                    val todayAttendance = attendance[todayDate] as? Map<String, String>
                    val time = todayAttendance?.get("time")

                    if (time != null && isTimeWithinRange(15, 16)) {
                        // Attendance already taken for the specified time range
                        showTimeRangeErrorDialog("Todays attendance is already marked")
                    } else {
                        // Attendance not taken for the specified time range
                        val intent = Intent(this, AttendanceActivity::class.java)
                        startActivity(intent)
                    }
                } else {
                    // Attendance not taken today
                    val intent = Intent(this, AttendanceActivity::class.java)
                    startActivity(intent)
                }
            } else {
                Toast.makeText(this, "No such document", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            // Handle the error
        }
    }

    private fun createCardView(cardItem: CardItem): CardItemBinding {
        val inflater = LayoutInflater.from(this)
        val cardBinding = CardItemBinding.inflate(inflater, binding.cardContainer, false)

        cardBinding.cardTitle.text = cardItem.title
        cardBinding.cardDescription.text = cardItem.description

        return cardBinding
    }

    private fun updateCardItems() {
        val cardItems = listOf(
            CardItem("Next class", "DSA T.E. in 14 minutes."), CardItem("Reprimands", reprimand)
        )

        binding.cardContainer.removeAllViews()
        for (cardItem in cardItems) {
            val cardBinding = createCardView(cardItem)
            binding.cardContainer.addView(cardBinding.root)
        }
    }

    private fun startAutoScroll(binding: ActivityDashboardBinding) {
        val handler = Handler(Looper.getMainLooper())
        val scrollRunnable = object : Runnable {
            override fun run() {
                val scrollX = binding.cardScrollView.scrollX
                val maxScrollX = binding.cardContainer.width - binding.cardScrollView.width

                if (scrollX < maxScrollX) {
                    binding.cardScrollView.smoothScrollTo(maxScrollX, 0)
                } else {
                    binding.cardScrollView.smoothScrollTo(0, 0)
                }

                handler.postDelayed(this, 4000)
            }
        }
        handler.postDelayed(scrollRunnable, 2000)
    }

    override fun onDestroy() {
        super.onDestroy()
        SharedPreferencesUtil.clearAllSharedPreferences(this)
    }
}