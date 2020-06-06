package com.exercises.bottextgame.models

data class Result (
    val surrender: String? = null,
    val attacker: String? = null,
    val defender: String? = null,
    val round: Int,
    val userStatus : PLayerStatus
)