package com.example.todolist

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.todolist.databinding.ActivityAddTodoBinding
import com.example.todolist.models.Todo
import com.example.todolist.signin.SignInActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class AddTodoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddTodoBinding
    private lateinit var todo: Todo
    private lateinit var oldTodo: Todo
    private var isUpdate = false
    private var selectedDate: java.util.Calendar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddTodoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        handleIntentData()
        setupUIComponents()

        binding.imgCheck.setOnClickListener { saveTodo() }
        binding.imgDelete.setOnClickListener { deleteTodo() }
        binding.imgBackArrow.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun saveTodo() {
        val title = binding.etTitle.text.toString()
        val todoDescription = binding.etNote.text.toString()

        if (title.isNotBlank() && todoDescription.isNotBlank()) {
            val formatter = SimpleDateFormat("EEE, d MMM yyyy HH:mm a", Locale.getDefault())
            todo = if (isUpdate) {
                Todo(oldTodo.id, title, todoDescription, formatter.format(java.util.Date()))
            } else {
                Todo(null, title, todoDescription, formatter.format(java.util.Date()))
            }

            // Si une date a été sélectionnée, ajouter au calendrier
            if (selectedDate != null) {
                val event = createGoogleCalendarEvent(title, todoDescription)
                val sharedPref = getSharedPreferences("my_prefs", Context.MODE_PRIVATE)
                val email = sharedPref.getString("user_email", null)

                if (email != null) {
                    insertEventToGoogleCalendar(event, email)
                } else {
                    Log.e("AddTodoActivity", "Email non disponible")
                    Toast.makeText(this, "Erreur : email non disponible", Toast.LENGTH_SHORT).show()
                }
            }

            setResultAndFinish()
        } else {
            Toast.makeText(this, "Veuillez saisir des données", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleIntentData() {
        oldTodo = intent.getSerializableExtra("current_todo") as? Todo ?: Todo(null, "", "", "")
        isUpdate = oldTodo.id != null
        binding.etTitle.setText(oldTodo.title)
        binding.etNote.setText(oldTodo.note)
        binding.imgDelete.visibility = if (isUpdate) View.VISIBLE else View.INVISIBLE
    }

    private fun setupUIComponents() {
        binding.imgCalendar.setOnClickListener {
            val intent = Intent(this, DatePickerActivity::class.java)
            datePickerLauncher.launch(intent)
        }
    }

    private val datePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getLongExtra("selected_date", -1)?.let { timestamp ->
                selectedDate = java.util.Calendar.getInstance().apply {
                    timeInMillis = timestamp
                }
                val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                Toast.makeText(
                    this,
                    "Date et heure sélectionnées : ${dateTimeFormat.format(selectedDate?.time)}",
                    Toast.LENGTH_SHORT
                ).show()

                addTodoToCalendar()
            }
        }
    }

    private fun addTodoToCalendar() {
        val selectedDate = this.selectedDate
        if (selectedDate == null) {
            return
        }

        // Stocker la date pour une utilisation ultérieure
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val formattedDate = dateFormatter.format(selectedDate.time)

        // Afficher un Toast pour confirmer la sélection de la date
        Toast.makeText(this, "Date sélectionnée : $formattedDate", Toast.LENGTH_SHORT).show()
    }


    private fun createGoogleCalendarEvent(title: String, description: String): Event {
        return Event().apply {
            summary = title
            setDescription(description)

            selectedDate?.let { date ->
                val adjustedDate = java.util.Calendar.getInstance().apply {
                    timeInMillis = date.timeInMillis
                    add(java.util.Calendar.HOUR_OF_DAY, -1)
                }

                val startDateTime = EventDateTime().apply {
                    dateTime = com.google.api.client.util.DateTime(adjustedDate.time)
                    timeZone = java.util.TimeZone.getDefault().id
                }
                start = startDateTime

                val endDate = java.util.Calendar.getInstance().apply {
                    timeInMillis = adjustedDate.timeInMillis
                    add(java.util.Calendar.HOUR_OF_DAY, 1)
                }

                val endDateTime = EventDateTime().apply {
                    dateTime = com.google.api.client.util.DateTime(endDate.time)
                    timeZone = java.util.TimeZone.getDefault().id
                }
                end = endDateTime

                Log.d("AddTodoActivity", "Date sélectionnée: ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(date.time)}")
                Log.d("AddTodoActivity", "Date ajustée: ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(adjustedDate.time)}")
            } ?: run {
                Log.e("AddTodoActivity", "Erreur : date et heure sélectionnées non disponibles")
                throw IllegalStateException("Date et heure sélectionnées non disponibles")
            }
        }
    }




    private fun insertEventToGoogleCalendar(event: Event, email: String) {
        try {
            Log.d("AddTodoActivity", "Début insertEventToGoogleCalendar")
            Log.d("AddTodoActivity", "Email reçu: $email")

            // Vérifier toutes les sources possibles d'email
            val account = GoogleSignIn.getLastSignedInAccount(this)
            var signInAccount = SignInActivity.getLastSignedInAccount()
            val sharedPref = getSharedPreferences("my_prefs", Context.MODE_PRIVATE)
            val savedEmail = sharedPref.getString("user_email", null)

            Log.d("AddTodoActivity", "Email du compte actuel: ${account?.email}")
            Log.d("AddTodoActivity", "Email du SignInActivity: ${signInAccount?.email}")
            Log.d("AddTodoActivity", "Email dans SharedPrefs: $savedEmail")

            // Afficher un Toast pour le débogage
            Toast.makeText(this, """
            Email param: $email
            Compte actuel: ${account?.email}
            SharedPrefs: $savedEmail
        """.trimIndent(), Toast.LENGTH_LONG).show()

            if (account == null) {
                Log.e("AddTodoActivity", "Compte Google non disponible - tentative de reconnexion")
                val intent = Intent(this, SignInActivity::class.java)
                startActivity(intent)
                return
            }

            Log.d("AddTodoActivity", "Tentative d'ajout avec email: ${account.email}")

            val credential = GoogleAccountCredential.usingOAuth2(
                this,
                listOf(CalendarScopes.CALENDAR)
            ).apply {
                selectedAccount = Account(account.email!!, "com.google")
            }

            Log.d("AddTodoActivity", "Credential créé")

            val transport = NetHttpTransport()
            val jsonFactory = GsonFactory.getDefaultInstance()

            val calendarService = Calendar.Builder(transport, jsonFactory, credential)
                .setApplicationName("My ToDo List")
                .build()

            Log.d("AddTodoActivity", "Service Calendar créé")

            // Exécuter la requête dans un thread séparé
            Thread {
                try {
                    val createdEvent = calendarService.events().insert("primary", event).execute()
                    runOnUiThread {
                        Log.d("AddTodoActivity", "Événement créé avec succès: ${createdEvent.htmlLink}")
                        Toast.makeText(this, "Tâche ajoutée au calendrier", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("AddTodoActivity", "Erreur lors de l'insertion de l'événement", e)
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            "Erreur lors de l'ajout au calendrier : ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }.start()

        } catch (e: Exception) {
            Log.e("AddTodoActivity", "Erreur lors de la configuration du service Calendar", e)
            Toast.makeText(
                this,
                "Erreur de configuration : ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun setResultAndFinish() {
        if (::todo.isInitialized) {
            val intent = Intent().apply {
                putExtra("todo", todo)
            }
            setResult(Activity.RESULT_OK, intent)
            finish()
        } else {
            Log.e("AddTodoActivity", "La propriété 'todo' n'a pas été initialisée avant d'appeler 'setResultAndFinish()'")
            Toast.makeText(this, "Erreur : tâche non initialisée", Toast.LENGTH_LONG).show()
        }
    }

    private fun deleteTodo() {
        val intent = Intent().apply {
            putExtra("todo", oldTodo as Serializable)
            putExtra("delete_todo", true)
        }
        setResult(Activity.RESULT_OK, intent)
        finish()
    }
}
