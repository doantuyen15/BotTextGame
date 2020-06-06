package com.exercises.bottextgame.models

class PLayerStatus(
    var playerHp: Long =100L,
    val playerName: String)
{
    constructor() : this(100L, "player")
}