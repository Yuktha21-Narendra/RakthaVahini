package com.yourname.rakthavahini

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class HomeActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var donorList = mutableListOf<Map<String, Any>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val spinnerBlood = findViewById<Spinner>(R.id.spinnerBloodGroup)
        val btnSearch = findViewById<Button>(R.id.btnSearch)
        val btnLogout = findViewById<Button>(R.id.btnLogout)
        val btnDonationLog = findViewById<Button>(R.id.btnDonationLog)
        val btnEmergency = findViewById<Button>(R.id.btnEmergency)
        val listView = findViewById<ListView>(R.id.listViewDonors)
        val switchAvailable = findViewById<Switch>(R.id.switchAvailable)

        val bloodGroups = arrayOf("All", "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")
        spinnerBlood.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, bloodGroups)

        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.collection("donors").document(uid).get()
                .addOnSuccessListener { doc ->
                    val isAvailable = doc.getBoolean("isAvailable") ?: true
                    switchAvailable.isChecked = isAvailable
                }
        }

        switchAvailable.setOnCheckedChangeListener { _, isChecked ->
            uid?.let {
                db.collection("donors").document(it)
                    .update("isAvailable", isChecked)
                Toast.makeText(this,
                    if (isChecked) "You are now available!" else "You are now unavailable",
                    Toast.LENGTH_SHORT).show()
            }
        }

        btnEmergency.setOnClickListener {
            Toast.makeText(this, "Finding all available donors...", Toast.LENGTH_SHORT).show()
            searchDonors("All", listView)
        }

        btnSearch.setOnClickListener {
            val selected = spinnerBlood.selectedItem.toString()
            searchDonors(selected, listView)
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val donor = donorList[position]
            val phone = donor["phone"] as? String ?: ""
            val name = donor["name"] as? String ?: "Donor"
            if (phone.isNotEmpty()) {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Call Donor")
                    .setMessage("Call $name at $phone?")
                    .setPositiveButton("Call") { _, _ ->
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                        startActivity(intent)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        btnDonationLog.setOnClickListener {
            startActivity(Intent(this, DonationLogActivity::class.java))
        }

        btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun searchDonors(bloodGroup: String, listView: ListView) {
        val ninetyDaysAgo = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000)
        val query = if (bloodGroup == "All") {
            db.collection("donors").whereEqualTo("isAvailable", true)
        } else {
            db.collection("donors")
                .whereEqualTo("bloodGroup", bloodGroup)
                .whereEqualTo("isAvailable", true)
        }

        query.get().addOnSuccessListener { result ->
            donorList.clear()
            val displayList = mutableListOf<String>()
            for (doc in result) {
                val lastDonation = doc.getLong("lastDonation")
                if (lastDonation != null && lastDonation > ninetyDaysAgo) {
                    continue
                }
                val name = doc.getString("name") ?: ""
                val phone = doc.getString("phone") ?: ""
                val bg = doc.getString("bloodGroup") ?: ""
                val city = doc.getString("city") ?: ""
                donorList.add(doc.data)
                displayList.add("$name | $bg | $city\nTap to Call: $phone")
            }
            if (displayList.isEmpty()) {
                displayList.add("No eligible donors found")
            }
            listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayList)
        }
    }
}