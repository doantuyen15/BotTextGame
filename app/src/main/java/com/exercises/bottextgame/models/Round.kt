package com.exercises.bottextgame.models

data class Round (
    val round: Int,
    val quizId: String,
    val syncTimer: Long? = System.currentTimeMillis()
)
