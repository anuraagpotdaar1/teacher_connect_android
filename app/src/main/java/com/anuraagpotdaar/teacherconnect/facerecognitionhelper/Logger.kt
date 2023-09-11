package com.anuraagpotdaar.teacherconnect.facerecognitionhelper

import com.anuraagpotdaar.teacherconnect.AttendanceActivity
class Logger {

    companion object {

        fun log(message: String) {
            AttendanceActivity.setMessage(AttendanceActivity.logTextView.text.toString() + "\n" + ">> $message")
            while (AttendanceActivity.logTextView.canScrollVertically(1)) {
                AttendanceActivity.logTextView.scrollBy(0, 10)
            }
        }
    }
}
