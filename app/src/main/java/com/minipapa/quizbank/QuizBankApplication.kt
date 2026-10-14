package com.minipapa.quizbank

import android.app.Application
import androidx.room.Room
import com.minipapa.quizbank.data.*
import com.google.firebase.FirebaseApp

class QuizBankApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.initializeApp(this) != null) AppCheckInstaller.install()
    }
    val database by lazy { Room.databaseBuilder(this, BankDatabase::class.java, "quizbank.db").build() }
    val repository by lazy { BankRepository(database) }
}
