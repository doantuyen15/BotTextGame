package com.exercises.bottextgame

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log

import androidx.appcompat.app.AppCompatActivity
import com.exercises.bottextgame.models.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.database.snapshot.ChildKey
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class MainActivity : AppCompatActivity() {
    private val commandRef = FirebaseDatabase.getInstance().getReference("/command")
    private val roomRef = FirebaseDatabase.getInstance().getReference("/gamerooms")
    val CHILD_ATTACKER_KEY = "Attack"
    val attacker = HashMap<String, Long>()
    private val playerStatusList = ArrayList<HashMap<String, Long>>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val userName = "admin@admin.com"
        val password = "123456"

        FirebaseAuth.getInstance().signInWithEmailAndPassword(userName, password)
            .addOnSuccessListener {
                trackingCommand()
//                trackingRoom()
            }
        button2.setOnClickListener {
            val test = HashMap<String, Any>()
            val player = HashMap<String, PLayerStatus>()
            player["uid1"] = PLayerStatus(playerName = "test1")
            player["uid2"] = PLayerStatus(playerName = "test2")
            test["start" + "-M8HXWXrvyyF9C3x5mcC"] = player
            commandRef.updateChildren(test)
        }

//        val taskQueue = ArrayList<String>()
        val time = ArrayList<Long>()
        time.add(5000L)
        time.add(3000L)
        var index = 0
        button.setOnClickListener {
            val test = HashMap<String, Any>()
//            i++
//            Log.d("Main", "starting task$i")
//            test["room$i"] = "$i"
//            getTask(test)
            val command = "attack-M8HXWXrvyyF9C3x5mcC"
            test["uid${index+1}"] = time[index]
            index++
            if(index >= 2){
                index = 0
            }
            commandRef.child(command).updateChildren(test)
        }
    }

    private fun startQuiz(        roomid: String?,        playerIdList: ArrayList<String>,        playerStatusList: ArrayList<PLayerStatus?>    ) {
//        Log.d("startQuiz", "HP is ")
        val currentRoom = roomid?.let { roomRef.child(it) }
        val job = GlobalScope.launch(Dispatchers.Default) {
//            val playerStatus = HashMap<String, Any>()
//            val playerHp = mutableListOf<Long>()
//            playerId.forEach {
//                playerHp.add(100)
//                playerStatus[it] = PLayerStatus(playerHp[playerId.indexOf(it)])
//            }
            var round: Int = 1
            repeat(5) { _ ->
                val result = HashMap<String, Any?>()
                result["quiz"] = Quiz(content = "đố m nè", answer = "ok")
                result["round"] = round
                currentRoom!!.updateChildren(result).await()
                val timeOut = 10000L
                val attackers = withContext(Dispatchers.Default) {
                    waitForAnswer(timeOut, roomid)
                }
                if (attackers.isNullOrEmpty()) {
                    result["surrender"] = "round$round"
                    result["attacker"] = null
                    result["defender"] = null
//                    playerHp.forEachIndexed{i,j -> playerHp[i]=j-5L}
                    var updateStatus = ""
                    playerStatusList.forEach { value ->
                        value?.let { it.playerHp -= 5L }
                    }
                } else {
                    val attacker = attackers[0]
                    Log.d("startQuiz", "attacker is $attacker")
                    val defender = attackers.getOrNull(1)
                    result["attacker"] = attacker
                    result["surrender"] = null
                    result["defender"] = defender
                    playerStatusList.forEachIndexed { index, value ->
                        if (index != playerIdList.indexOf(attacker)) {
                            value!!.playerHp -= 5L
                        }
                    }
                    defender?.let{
                        Log.d("startQuiz", "defender is $defender")
                        playerStatusList[playerIdList.indexOf(it)]!!.playerHp += 2
                    }
                }
                round++
                result["userStatus"] = playerIdList.zip(playerStatusList).toMap()
                result["round"] = round
//                Log.d("startQuiz", "HP is $playerHp")
                currentRoom.updateChildren(result).await()
                delay(5000)
            }
        }
    }

    private suspend fun waitForAnswer(timeOut: Long, roomid: String?): List<String> {
        val attackerId: MutableList<String> = mutableListOf()
        Log.d("$roomid", "waiting for answer")
        commandRef.child("attack$roomid").setValue(null).await()
        commandRef.child("attack$roomid").orderByValue().addValueEventListener(object: ValueEventListener{
            override fun onCancelled(p0: DatabaseError) {
            }

            override fun onDataChange(p0: DataSnapshot) {
                p0.children.mapIndexed { index, attacker ->
                    if (index <= 1)
                        attackerId.add(index, attacker.key.toString())
                }
            }
        })
        withTimeoutOrNull(timeOut){
            while (attackerId.isEmpty()){
                if(isActive){
                    delay(500L)
                } //Log.d("waitForAnswer", "timeout*******")
            }
            delay(1500L)
        }
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

    private fun trackingCommand() {
        commandRef.addChildEventListener(object : ChildEventListener {
            override fun onCancelled(p0: DatabaseError) {
            }

            override fun onChildMoved(p0: DataSnapshot, p1: String?) {
            }

            override fun onChildChanged(p0: DataSnapshot, p1: String?) {
            }

            override fun onChildAdded(p0: DataSnapshot, p1: String?) {
                if (p0.key!!.contains("start")) {
                    val playerIdList = ArrayList<String>()
                    val playerStatusList = ArrayList<PLayerStatus?>()
                    p0.children.forEach{
                        playerIdList.add(it.key.toString())
                        playerStatusList.add(it.getValue(PLayerStatus::class.java))
                    }
                    startQuiz(p0.key?.substringAfter("start"), playerIdList, playerStatusList)
                    //delete command after finish task
                    commandRef.child(p0.key!!).setValue(null)
                    Log.d("trackingCommand", "added****************${p0.key}")
                }
            }

            override fun onChildRemoved(p0: DataSnapshot) {
            }


        })
    }

    /*private fun trackingRoom() {
        roomRef.addChildEventListener(object : ChildEventListener {
            override fun onCancelled(p0: DatabaseError) {
                //
            }

            override fun onChildMoved(p0: DataSnapshot, p1: String?) {
                //
            }

            override fun onChildChanged(p0: DataSnapshot, p1: String?) {
                addNewPlayer(p0)
//                Log.d("trackingRoom","RoomChanged****************${p1}")
            }

            override fun onChildAdded(p0: DataSnapshot, p1: String?) {
                addNewPlayer(p0)
            }

            override fun onChildRemoved(p0: DataSnapshot) {
                //
            }

        })
    }*/

    /*private fun addNewPlayer(p0: DataSnapshot) {
        p0.child("userStatus").children.forEach {
            val playerStatus = HashMap<String, Long>()
            playerStatus[it.key.toString()] = it.child("playerHp").value as Long
            if (!playerStatusList.contains(playerStatus)) {
                playerStatusList.add(playerStatus)
                Log.d("TrackingRoom", "addNewPlayer****************${playerStatusList}")
            }

        }
    }*/


//    private suspend fun doTask(task: Int): String {
//        delay((1000L..5000L).random())
//        Log.d("Main", "thread$task OK .........")
//        return "task $task ...OK"
//    }

}
