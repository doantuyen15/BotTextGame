package com.exercises.bottextgame

import android.util.Log
import com.exercises.bottextgame.models.Message
import com.exercises.bottextgame.models.Result
import com.exercises.bottextgame.models.Round
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.tasks.await

@ExperimentalCoroutinesApi
class GameService(private val roomId: String) {
    private val playerStatusList = ArrayList<Result>()
    private val playerList = HashMap<String, String>()
    private var multiMode: Boolean = false
    private var isPlaying = false
    private var round = 0
    private val quizId = (1..dbSorted.size).toMutableList()
    private var totalPlayer = 0
    private var isQuit = false
    var attackers: MutableList<DataSnapshot> = mutableListOf()

    private val roomCommandRef = commandRef.child(roomId)
    private val currentRoomRef = roomRef.child(roomId)

    private fun waitForAnswer(timeOut: Long = 0L){
        val scopeAttacker = CoroutineScope(Dispatchers.Default)
        scopeAttacker.launch {
            Log.d("flowAttacking in ${Thread.currentThread().name}", "scope launch")
            withTimeoutOrNull(timeOut) {
                flowAttacking.collect {
                    delay(1500L)
                    Log.d("flowAttacking", "scope cancel")
//                sendRoundResult(attackers)
                    cancel()
                }
            }
            cancel()
            Log.d("wait for answer in thread: ${Thread.currentThread().name}", "scope active? =$isActive")
        }
    }

    private fun checking() {
        val scopeChecking = CoroutineScope(Dispatchers.Default)
        scopeChecking. launch {
            Log.d("checking in thread: ${Thread.currentThread().name}", "round = $round")
            val checkList = playerList
            currentRoomRef.child(CHILD_ROOMSTATUS_KEY).setValue("checking").await()
            withTimeoutOrNull(2500L) {
                flowChecking.collect { player ->
                    checkList.remove(player)
                }
            }
            var message = ""
            if (checkList.count() == totalPlayer) {//All disconnected
                quitAndClearRoom()
            } else if (checkList.count() != 0 && !isQuit) {
                checkList.forEach { player ->
                    message += "${player.key} was offline\n"
                }
                currentRoomRef.child(CHILD_MESSAGE_KEY)
                    .push()
                    .setValue(Message(message))
            }
            delay(2500L)
            cancel()
            if(!isQuit){
                pushQuiz()
            }
            Log.d("checking in thread: ${Thread.currentThread().name}", "scope active? =$isActive")
        }
    }

    private val handleListener = object : ValueEventListener {
        override fun onCancelled(p0: DatabaseError) {
        }

        override fun onDataChange(p0: DataSnapshot) {
            handleCommand(p0)
        }

    }

    fun create() {
        currentRoomRef.child(CHILD_MESSAGE_KEY)
            .push()
            .setValue(Message(message = "Welcome to Text Game\nType '.start' to play \nor '.help' for more info"))
        roomCommandRef.addValueEventListener(handleListener)
    }

    private fun handleCommand(p0: DataSnapshot) {
        val commandKey = p0.value.toString()
        when (commandKey) {
            "quit" -> quitAndClearRoom()
            "start" -> start()
            "restart" -> restart()
//            else -> {
//                if (commandKey.contains("kick")) {
//                    val kickedPlayerId = commandKey.substringAfter("kick")
//                    playerList.remove(kickedPlayerId)
//                    playerStatusList.forEachIndexed { index, player ->
//                        if (player.playerId == kickedPlayerId) {
//                            playerStatusList.removeAt(index)
//                        }
//                    }
//                }
//            }
        }
    }

    private fun restart() {
        roomRef.apply {
            child(roomId).child(CHILD_ROOMSTATUS_KEY)
                .setValue("open")
            child(CHILD_LISTROOMS_KEY).child(roomId).child(CHILD_ROOMSTATUS_KEY)
                .setValue("open")
        }
    }

    private fun start() {
        roomRef.apply {
            child(roomId).child(CHILD_JOINEDUSER_KEY)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onCancelled(p0: DatabaseError) {
                    }

                    override fun onDataChange(p0: DataSnapshot) {
                        if (p0.childrenCount == 1L) {
                            multiMode = false
                            val playerInfo = p0.children.first()
                            val id = playerInfo.key.toString()
                            val playerName = playerInfo.value.toString()
                            playerList[id] = playerName
                            playerStatusList.add(Result(playerName, id))
                        } else {
                            multiMode = true
                            p0.children.forEach {
                                val id = it.key.toString()
                                val playerName = it.value.toString()
                                playerList[id] = playerName
                                playerStatusList.add(Result(playerName, id))
                            }
                        }
                        totalPlayer = playerStatusList.count()
                        checking()
                    }
                })
            child(roomId).child(CHILD_MESSAGE_KEY)
                .push()
                .setValue(Message("Game Start!"))
            child(CHILD_LISTROOMS_KEY).child(roomId).child(CHILD_ROOMSTATUS_KEY)
                .setValue("start")
        }

    }

    private fun quitAndClearRoom() {
//        scopeCancel()
        isQuit = true
        apply {
            roomCommandRef.removeEventListener(handleListener)
            roomCommandRef.setValue(null)
            roomRef.child(CHILD_LISTROOMS_KEY).child(roomId).setValue(null)
            currentRoomRef.setValue(null)
        }
    }

    private val flowAttacking = callbackFlow<MutableList<DataSnapshot>> {
        attackers = mutableListOf()
        val databaseReference = commandRef.child(roomId).orderByValue()
        val eventListener = databaseReference.addValueEventListener(object : ValueEventListener {
            override fun onCancelled(p0: DatabaseError) {
                this@callbackFlow.close(p0.toException())
            }

            override fun onDataChange(p0: DataSnapshot) {
                if (p0.value.toString() == "quit") {
                    cancel()
                }
                if (p0.hasChildren()) {
                    attackers = p0.children.toMutableList()
                    this@callbackFlow.sendBlocking(attackers)
                }
            }
        })
        awaitClose {
            if(!isQuit){
                sendRoundResult(attackers)
            }
            databaseReference.removeEventListener(eventListener)
        }
    }

    private val flowChecking = callbackFlow<String> {
        val databaseReference = currentRoomRef.child(CHILD_ROOMSTATUS_KEY).child("checking")
        val eventListener = databaseReference.addChildEventListener(object : ChildEventListener {
            override fun onCancelled(p0: DatabaseError) {
                this@callbackFlow.close(p0.toException())
            }

            override fun onChildMoved(p0: DataSnapshot, p1: String?) {

            }

            override fun onChildChanged(p0: DataSnapshot, p1: String?) {

            }

            override fun onChildAdded(p0: DataSnapshot, p1: String?) {
                try{
                    this@callbackFlow.sendBlocking(p0.key.toString())
                } catch (e: CancellationException) {
                    Log.d("flowChecking", "error on child added")
                }

            }

            override fun onChildRemoved(p0: DataSnapshot) {

            }

        })
        awaitClose {
            databaseReference.removeEventListener(eventListener)
        }
    }

    private fun pushQuiz() {
        round++
        val command = HashMap<String, Any?>()
        val randomId = quizId.random()
        quizId.remove(randomId)
        command["round"] = Round(round, randomId.toString())
        if(!isQuit) {
            commandRef.child(roomId)
                .setValue(null)
            currentRoomRef.updateChildren(command)
                .addOnCompleteListener {
                    val timeOut = dbSorted[randomId]?.timeOut ?: 0L
                    waitForAnswer(timeOut)
                }
        }
    }

    private fun sendRoundResult(attackers: MutableList<DataSnapshot>) {
        val command = HashMap<String, Any?>()
        val attacker = attackers.getOrNull(0)?.value.toString()
        val defender = attackers.getOrNull(1)?.value.toString()
        var messageAtk: String = ""
        var messageDef: String = ""
        command[CHILD_ROOMSTATUS_KEY] = "endRound"
        playerStatusList.map {
            when (it.playerId) {
                attacker -> {
                    it.increaseAttack()
                    messageAtk = ("First: ${it.playerName}")
                }
                defender -> {
                    it.increaseDefend()
                    messageDef += ("\nSecond is: ${it.playerName}")
                }
                else -> it.increaseSurrender()
            }
            if (it.isDeadOrOut()) { // return false when this player is out or lose
                totalPlayer--
            }
        }
        val status = HashMap<String, Any>()
        playerStatusList.sortByDescending { it.hp }
        playerStatusList.forEach {
            status[it.playerId] = it.toMap()
        }

//                command["playerStatus"] = playerIdList.zip(playerStatusList).toMap()
        command["playerStatus"] = status
        var message = messageAtk
        if (!messageDef.isBlank()) {
            message += messageDef
        }
        if (message.isNotEmpty()) {
            currentRoomRef.child("message")
                .push()
                .setValue(Message(message = message))
        }
        currentRoomRef.updateChildren(command)
        if (!multiMode && totalPlayer == 0) {
            gameFinish()
        } else if (multiMode && totalPlayer <= 1) {
            gameFinish()
        } else {
            checking()
        }
//            .await()
//        delay(5000)
    }

    private fun gameFinish() {
        apply {
            currentRoomRef.child(CHILD_ROOMSTATUS_KEY)
                .setValue("finish")
            roomRef.child(CHILD_LISTROOMS_KEY).child(roomId).child(CHILD_ROOMSTATUS_KEY)
                .setValue("finish")
        }
    }
}