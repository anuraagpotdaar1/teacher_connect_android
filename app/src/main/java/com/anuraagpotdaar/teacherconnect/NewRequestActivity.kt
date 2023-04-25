package com.anuraagpotdaar.teacherconnect

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import com.anuraagpotdaar.teacherconnect.databinding.ActivityNewRequestBinding

class NewRequestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding: ActivityNewRequestBinding = ActivityNewRequestBinding.inflate(layoutInflater)

        setContentView(binding.root)

        val reqTypeList = resources.getStringArray(R.array.request_types)
        val arrayAdapter = ArrayAdapter(this, R.layout.item_request_type, reqTypeList)
        binding.autoCompleteTextView.setAdapter(arrayAdapter)
    }
}