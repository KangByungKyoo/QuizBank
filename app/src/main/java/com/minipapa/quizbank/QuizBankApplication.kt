package com.minipapa.quizbank

import android.app.Application
import androidx.room.Room
import com.minipapa.quizbank.data.*

class QuizBankApplication : Application() {
    val database by lazy { Room.databaseBuilder(this, BankDatabase::class.java, "quizbank.db").build() }
    val repository by lazy { BankRepository(database) }
}
