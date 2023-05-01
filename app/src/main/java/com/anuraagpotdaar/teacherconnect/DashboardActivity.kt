package com.anuraagpotdaar.teacherconnect

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.anuraagpotdaar.teacherconnect.databinding.ActivityDashboardBinding
import com.anuraagpotdaar.teacherconnect.databinding.CardItemBinding
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDashboardBinding
    private lateinit var name: String

    private lateinit var reprimand: String

    data class CardItem(val title: String, val description: String)

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
            intent.putExtra("Username", name)
            startActivity(intent)
        }

        binding.btnAttendance.setOnClickListener {
            val intent = Intent(this, AttendanceActivity::class.java)
            intent.putExtra("Username", name)
            startActivity(intent)
        }
        startAutoScroll(binding)
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
                val availableLeaves = snapshot.getLong("Prev_postings.availableLeaves")?.toInt()

                val reprimandsList = snapshot.get("Reprimands") as? List<Map<String, Any>> ?: emptyList()

                var latestTimestamp: Date? = null
                var latestRemark: String? = null

                for (reprimand in reprimandsList) {
                    val timestampStr = reprimand["timestamp"] as? String
                    val timestampFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
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
}