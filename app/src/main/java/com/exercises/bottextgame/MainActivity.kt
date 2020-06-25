package com.exercises.bottextgame

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.exercises.bottextgame.models.Message
import com.exercises.bottextgame.models.Quiz
import com.exercises.bottextgame.models.Result
import com.exercises.bottextgame.models.Round
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class MainActivity : AppCompatActivity() {


    @ExperimentalCoroutinesApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val userName = "admin@admin.com"
        val password = "123456"

        FirebaseAuth.getInstance().signInWithEmailAndPassword(userName, password)
            .addOnSuccessListener {
                trackingCommand()
            }
        query.collection("quiz").get().addOnSuccessListener {
            it.documents.forEach { doc ->
                dbSorted[doc.id.toInt()] = doc.toObject(Quiz::class.java)!!
            }
            Log.d("database", "OK")
        }

        button.setOnClickListener {
            commandRef.child("asd")
                .updateChildren(mapOf("${(1000..2000).random()}result" to "uid1"))
        }

        var i = 1
        button2.setOnClickListener {
//            GameService().observe()
//            commandRef.apply {
//                child("aaa").updateChildren(mapOf(i.toString() to "value $i"))
//                val room = push()
//                    room.setValue(i.toString()).addOnCompleteListener {
//                    room.updateChildren(mapOf("test" to i))
//                }
//            }
//            i++
        }
    }

    private fun startQuiz(
        roomId: String,
        playerStatusList: ArrayList<Result>,
        multi: Boolean
    ) {
//        Log.d("startQuiz", "HP is ")
        var endGame = false
        val currentRoom = roomRef.child(roomId)
        val job = GlobalScope.launch(Dispatchers.IO) {
            var totalPlayer = playerStatusList.count()
            val quizId = (1..dbSorted.size).toMutableList()

//            val playerStatus = HashMap<String, Any>()
//            val playerHp = mutableListOf<Long>()
//            playerId.forEach {
//                playerHp.add(100)
//                playerStatus[it] = PLayerStatus(playerHp[playerId.indexOf(it)])
//            }

            var round = 1
            while (!endGame) {
                val command = HashMap<String, Any?>()
                val randomId = quizId.random()
                quizId.remove(randomId)
                command["round"] = Round(round, randomId.toString())
                currentRoom.updateChildren(command).await()
                val timeOut = dbSorted[randomId]?.timeOut ?: 0L
                val attackers = withContext(Dispatchers.IO) {
                    waitForAnswer(timeOut, roomId)
                }
                //send to client round's result
                val attacker = attackers.getOrNull(0).toString()
                val defender = attackers.getOrNull(1).toString()
                var messageAtk: String = ""
                var messageDef: String = ""
                if (!attackers.isNullOrEmpty()) {
                    command["attacker"] = attacker
                    attackers.getOrNull(1)?.let { command["defender"] = defender }
                    command["surrender"] = null
                } else {
                    command["surrender"] = "round$round"
                    command["attacker"] = null
                    command["defender"] = null
                }
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
                    if(it.isDeadOrOut()) { // return false when this player is out or lose
                        totalPlayer--
                    }
                }
//                playerStatusList.forEachIndexed { index, status ->
//                    val playerId = ""
//                    when (index) {
//                        playerIdList.indexOf(attacker) -> status.increaseAttack()
//                        playerIdList.indexOf(defender) -> status.increaseDefend()
//                        else -> status.increaseSurrender()
//                    }
//                    if(status.isDeadOrOut()) { // return false when this player is out or lose
//                        totalPlayer--
//                    }
////                    if (index != playerIdList.indexOf(attacker) && index != playerIdList.indexOf(defender)) {
////                        status[attacker]!!.
////                    }
////                    if (index == playerIdList.indexOf(defender)) {
////                        value!!.playerHp -= 3L
////                    }
////                    if (value!!.playerHp <= 0L) {
////                        totalPlayer--
////                    }
//               }
                if (!multi && totalPlayer == 0) {
                    endGame = true
                } else if (multi && totalPlayer <= 0) {
                    endGame = true
                } else round++

                val status = HashMap<String, Any>()
                playerStatusList.sortByDescending{it.hp}
                playerStatusList.forEach {
                    status[it.playerId] = it.toMap()
                }

//                command["playerStatus"] = playerIdList.zip(playerStatusList).toMap()
                command["playerStatus"] = status
                var message = messageAtk
                if (!messageDef.isBlank()){
                    message += messageDef
                }
                if (message.isNotEmpty()){
                    currentRoom.child("message").push().setValue(Message(message = message))
                }
                currentRoom.updateChildren(command).await()
                delay(5000)
            }
            //end game
            if (endGame){
                currentRoom.child("roomStatus").setValue("ending")
            }
        }
        val mEventListener = object: ValueEventListener{
            override fun onCancelled(p0: DatabaseError) {
            }
            override fun onDataChange(p0: DataSnapshot) {
                if (p0.value == true){
                    endGame = true
                    currentRoom.setValue(null)
                    commandRef.child("clear$roomId").setValue(null)
                }
            }
        }
        commandRef.child("clear$roomId").addValueEventListener(mEventListener)
    }

//    private fun sendResult(
//        currentRoom: String?,
//        result: Map<String, Any>
//    ) {
//        val command = mapOf("result" to result)
//        roomRef.child(currentRoom!!).updateChildren(command)
//    }

//    private suspend fun attackerListener() {
//        val listener = object : ValueEventListener {
//            override fun onCancelled(p0: DatabaseError) {
//            }
//
//            override fun onDataChange(p0: DataSnapshot) {
//                p0.children.mapIndexed { index, attacker ->
////                if (index <= 1)
////                    attackerId.add(index, attacker.key.toString())
//                }
//            }
//        }
//    }

//    interface FirebaseCallBack{
////        val attackerId: String
//        fun onReceivedAttacker()
//    }

    private suspend fun waitForAnswer(timeOut: Long, roomid: String): List<String> {
        val attackerId: MutableList<String> = mutableListOf()
//        Log.d("$roomid", "waiting for answer")
        commandRef.child("attack$roomid").setValue(null).await()
//        val fbCallBack = object: FirebaseCallBack{
//            override fun onReceivedAttacker(){
//            }
//        }
        val listener = object : ValueEventListener {
            var first = timeOut
            var second = timeOut
            override fun onCancelled(p0: DatabaseError) {
            }

            override fun onDataChange(p0: DataSnapshot) {
                p0.children.forEach {
                    val timeStamp = it.key!!.toLong()
                    val playerId = it.value.toString()
                    if(timeStamp < first){
                        first = timeStamp
                        attackerId.add(0, playerId)
                    } else if (timeStamp < second){
                        second = timeStamp
                        attackerId.add(1, playerId)
                    }
                }
            }
        }

        val query = commandRef.child("attack$roomid").orderByValue()
        query.addValueEventListener(listener)
        withTimeoutOrNull(timeOut){
            while (attackerId.isEmpty()){
                if(isActive){
                    delay(200L)
                } //Log.d("waitForAnswer", "timeout*******")
            }
            delay(1500L)
        }
        query.removeEventListener(listener)
//        withTimeoutOrNull(timeOut){
//            val attackerId = UserObserver(roomid).observe()
//        }
        return attackerId.distinct()
    }

//    interface CommandListener{
//        fun onCommandRetrieve(p0: DataSnapshot)
//        fun onStart()
//        fun onFailure(p0: DatabaseError)
//    }

//    private fun commandEventListener(listener: CommandListener) {
//        listener.onStart()
//        commandRef.addChildEventListener(object : ChildEventListener {
//            override fun onCancelled(p0: DatabaseError) {
//                listener.onFailure(p0)
//                Log.d("Main", "cancel****************")
//            }
//
//            override fun onChildMoved(p0: DataSnapshot, p1: String?) {
//                //
//            }
//
//            override fun onChildChanged(p0: DataSnapshot, p1: String?) {
//                Log.d("Main", "Changed****************${p0.key}")
////                if(p0.key!!.contains("start")){
////                    startQuiz(p0.key?.substringAfter("start"))
////                }
//
//
//            }
//
//            override fun onChildAdded(p0: DataSnapshot, p1: String?) {
//                listener.onCommandRetrieve(p0)
//
//            }
//
//            override fun onChildRemoved(p0: DataSnapshot) {
//            }
//
//        })
//    }

    @ExperimentalCoroutinesApi
    private fun trackingCommand() {
        roomRef.child(CHILD_LISTROOMS_KEY).addChildEventListener(object : ChildEventListener {
            override fun onCancelled(p0: DatabaseError) {
            }

            override fun onChildMoved(p0: DataSnapshot, p1: String?) {
            }

            override fun onChildChanged(p0: DataSnapshot, p1: String?) {
            }

            override fun onChildAdded(p0: DataSnapshot, p1: String?) {
                if (p0.key.toString() != "preventRemoveKey") {
                    handleCommand(p0)
                }
            }

            override fun onChildRemoved(p0: DataSnapshot) {
            }


        })
    }

    @ExperimentalCoroutinesApi
    private fun handleCommand(p0: DataSnapshot) {
        val roomKey = p0.key.toString()
        if (p0.child(CHILD_ROOMSTATUS_KEY).value.toString() == "create") {
            GameService(roomKey).create()
        }
//        if (p0.key!!.contains("start")) {
////            val playerIdList = ArrayList<String>()
//            val playerStatusList = ArrayList<Result>()
////            val playerStatus = Result()
//            p0.children.forEach{
//                val id = it.key.toString()
//                val playerName = it.value.toString()
////                playerIdList.add(it.key.toString())
//                playerStatusList.add(Result(playerName, id))
//            }
//            val checkMulti = p0.childrenCount != 1L
//            startQuiz(roomKey, playerStatusList, checkMulti)
            //delete command after finish task
//            commandRef.child(p0.key!!).setValue(null)
//        }
    }

//    override fun onPause() {
//        super.onPause()
//        FirebaseDatabase.getInstance().purgeOutstandingWrites()
//    }
}