package com.example.todolist

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.todolist.adaptors.TodoAdapter
import com.example.todolist.database.TodoDatabase
import com.example.todolist.databinding.ActivityMainBinding
import com.example.todolist.models.Todo
import com.example.todolist.models.TodoViewModel
import com.example.todolist.signin.SignInActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.api.services.calendar.CalendarScopes

class MainActivity : AppCompatActivity(), TodoAdapter.TodoClickListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var database: TodoDatabase
    lateinit var viewModel: TodoViewModel
    lateinit var adapter: TodoAdapter

    private val launcher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                handleSignInResult(task)
            }
        }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)

            val email = account?.email
            val displayName = account?.displayName

            val sharedPref = getSharedPreferences("my_prefs", Context.MODE_PRIVATE)
            with(sharedPref.edit()) {
                putString("user_email", email)
                apply()
            }

            Toast.makeText(this, "Connecté en tant que $displayName", Toast.LENGTH_SHORT).show()

        } catch (e: ApiException) {
            Log.w("MainActivity", "signInResult:failed code=" + e.statusCode)
        }
    }

    private val getContent =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val todo = result.data?.getSerializableExtra("todo") as? Todo
                todo?.let {
                    viewModel.insertTodo(it)
                }
            }
        }

    private val updateOrDeleteTodo =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val todo = result.data?.getSerializableExtra("todo") as? Todo
                val isDelete = result.data?.getBooleanExtra("delete_todo", false) ?: false
                if (todo != null) {
                    if (isDelete) {
                        viewModel.deleteTodo(todo)
                    } else {
                        viewModel.updateTodo(todo)
                    }
                }
            }
        }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(CalendarScopes.CALENDAR))
            .build()
        val googleSignInClient = GoogleSignIn.getClient(this, gso)

        val account = GoogleSignIn.getLastSignedInAccount(this)
        val sharedPref = getSharedPreferences("my_prefs", Context.MODE_PRIVATE)

        // Débuggage des informations utilisateur
        val userEmail = account?.email ?: "Email non disponible"
        val userName = account?.displayName ?: "Nom non disponible"
        Log.d("MainActivity", "Email: $userEmail")
        Log.d("MainActivity", "Nom: $userName")

        // Vérifier aussi les SharedPreferences
        val savedEmail = sharedPref.getString("user_email", "Non trouvé dans SharedPrefs")
        val savedName = sharedPref.getString("user_display_name", "Non trouvé dans SharedPrefs")
        Log.d("MainActivity", "Email dans SharedPrefs: $savedEmail")
        Log.d("MainActivity", "Nom dans SharedPrefs: $savedName")

        account?.email?.let { email ->
            with(sharedPref.edit()) {
                putString("user_email", email)
                apply()
            }
        } ?: run {
            val intent = Intent(this, SignInActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Afficher les informations dans l'interface
        binding.userNameTextView.text = "Bienvenue, ${account.displayName ?: "Utilisateur"}"
        binding.logout.setOnClickListener {
            signOut()
        }

        // Afficher un Toast pour le débogage
        Toast.makeText(this,
            "Compte connecté:\nNom: ${account.displayName}\nEmail: ${account.email}",
            Toast.LENGTH_LONG
        ).show()

        initUI()

        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        ).get(TodoViewModel::class.java)

        viewModel.allTodo.observe(this) { list ->
            list?.let {
                adapter.updateList(list)
            }
        }

        database = TodoDatabase.getDatabase(this)
    }


    private fun initUI() {
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        adapter = TodoAdapter(this, this)
        binding.recyclerView.adapter = adapter

        binding.fabAddTodo.setOnClickListener {
            val intent = Intent(this, AddTodoActivity::class.java)
            getContent.launch(intent)
        }
    }

    override fun onItemClicked(todo: Todo) {
        val intent = Intent(this@MainActivity, AddTodoActivity::class.java)
        intent.putExtra("current_todo", todo)
        updateOrDeleteTodo.launch(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                signOut()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun signOut() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(CalendarScopes.CALENDAR))
            .build()
        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        googleSignInClient.revokeAccess().addOnCompleteListener(this) {
            googleSignInClient.signOut().addOnCompleteListener(this) {
                val intent = Intent(this, SignInActivity::class.java)
                startActivity(intent)
                finish()
            }
        }
    }



}
