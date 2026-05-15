package com.yourname.rakthavahini

import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar

class DonationLogActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var selectedDateMillis: Long = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_donation_log)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        createNotificationChannel()

        val btnPickDate = findViewById<Button>(R.id.btnPickDate)
        val tvDate = findViewById<TextView>(R.id.tvDonationDate)
        val btnSave = findViewById<Button>(R.id.btnSaveDonation)
        val listView = findViewById<ListView>(R.id.listDonations)

        loadDonationHistory(listView)

        btnPickDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, day ->
                val picked = Calendar.getInstance()
                picked.set(year, month, day)
                selectedDateMillis = picked.timeInMillis
                tvDate.text = "Selected: $day/${month + 1}/$year"
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        btnSave.setOnClickListener {
            val uid = auth.currentUser?.uid ?: return@setOnClickListener
            val log = hashMapOf(
                "date" to selectedDateMillis,
                "uid" to uid
            )
            db.collection("donors").document(uid)
                .collection("donations").add(log)
                .addOnSuccessListener {
                    db.collection("donors").document(uid)
                        .update("lastDonation", selectedDateMillis)
                    Toast.makeText(this, "Donation logged!", Toast.LENGTH_SHORT).show()
                    sendThankYouNotification()
                    loadDonationHistory(listView)
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to save. Try again.", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "donation_channel",
                "Donation Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thank you notifications for blood donation"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun sendThankYouNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
                return
            }
        }

        val notification = NotificationCompat.Builder(this, "donation_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Thank You, Hero!")
            .setContentText("Your donation has been logged. You may have saved a life!")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Thank you for donating blood! Your generous act may have saved " +
                            "someone's life today. You are a true hero! " +
                            "Your next eligible donation date will be after 90 days."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this).notify(1001, notification)
    }

    private fun loadDonationHistory(listView: ListView) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("donors").document(uid)
            .collection("donations")
            .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                val logs = mutableListOf<String>()
                for (doc in result) {
                    val dateMillis = doc.getLong("date") ?: continue
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = dateMillis
                    val day = cal.get(Calendar.DAY_OF_MONTH)
                    val month = cal.get(Calendar.MONTH) + 1
                    val year = cal.get(Calendar.YEAR)
                    val nextEligible = Calendar.getInstance()
                    nextEligible.timeInMillis = dateMillis
                    nextEligible.add(Calendar.DAY_OF_MONTH, 90)
                    val nd = nextEligible.get(Calendar.DAY_OF_MONTH)
                    val nm = nextEligible.get(Calendar.MONTH) + 1
                    val ny = nextEligible.get(Calendar.YEAR)
                    logs.add("Donated: $day/$month/$year\nNext eligible: $nd/$nm/$ny")
                }
                if (logs.isEmpty()) logs.add("No donations logged yet")
                listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, logs)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load history.", Toast.LENGTH_SHORT).show()
            }
    }
}