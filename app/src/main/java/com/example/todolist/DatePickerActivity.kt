package com.example.todolist

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.CalendarView
import android.widget.ImageView
import android.widget.TimePicker
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar


class DatePickerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_date_picker)

        val calendarView = findViewById<CalendarView>(R.id.calendarView)
        val timePicker = findViewById<TimePicker>(R.id.timePicker)
        val backButton = findViewById<ImageView>(R.id.img_back_arrow)

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val selectedDate = Calendar.getInstance().apply {
                set(year, month, dayOfMonth, timePicker.hour, timePicker.minute)
            }
            val intent = Intent().apply {
                putExtra("selected_date", selectedDate.timeInMillis)
            }
            setResult(Activity.RESULT_OK, intent)
            finish()
        }

        backButton.setOnClickListener {
            finish()
        }
    }
}
