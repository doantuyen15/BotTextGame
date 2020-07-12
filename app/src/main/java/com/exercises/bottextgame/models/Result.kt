package com.exercises.bottextgame.models

data class Result(
    val playerName: String,
    var playerId: String,
    var hp: Long = 10L,
    private var attack: Long = 0L,
    private var defend: Long = 0L,
    private var surrender: Long = 0L,
    private var isDead: Boolean = false,
    private var isConformDead: Boolean = false)
{
    fun increaseAttack() {
        if(!isDead){
            this.attack++
        }
    }
    fun increaseDefend() {
        if(!isDead){
            this.defend++
            decreaseHp(3L)
        }
    }
    fun increaseSurrender() {
        if(!isDead){
            this.surrender++
            decreaseHp(5L)
        }
    }
    fun isDeadOrOut(): Boolean {
        return if(this.hp <= 0 && !isConformDead){
            isConformDead = true
            isConformDead
        } else {
            false
        }
    }
    fun resetStatus() {
        this.hp = 100L
        this.attack = 0L
        this.defend = 0L
        this.surrender = 0L
        this.isDead = false
        this.isConformDead = false
    }
    private fun decreaseHp(damage: Long) {
        if (this.hp >= damage){
            this.hp -= damage
        } else {
            this.hp = 0L
            this.isDead = true
        }
    }
    fun toMap(): Map<String, Any> {
        return mapOf(
            "playerName" to this.playerName,
            "hp" to this.hp,
            "attack" to attack,
            "defend" to defend,
            "surrender" to surrender
        )
    }
//    constructor() : this(0L, 0L, 0L)
}
