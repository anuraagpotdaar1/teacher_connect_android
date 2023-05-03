package com.anuraagpotdaar.teacherconnect.facerecognitionhelper

import com.anuraagpotdaar.teacherconnect.AttendanceActivity

// Logs message using log_textview present in activity_main.xml
class Logger {

    companion object {

        fun log(message: String) {
            AttendanceActivity.setMessage(  AttendanceActivity.logTextView.text.toString() + "\n" + ">> $message" )
            // To scroll to the last message
            // See this SO answer -> https://stackoverflow.com/a/37806544/10878733
            while ( AttendanceActivity.logTextView.canScrollVertically(1) ) {
                AttendanceActivity.logTextView.scrollBy(0, 10)
            }
        }

    }

}