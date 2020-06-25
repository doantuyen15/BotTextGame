package com.exercises.bottextgame

import com.exercises.bottextgame.models.Quiz
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore

val commandRef = FirebaseDatabase.getInstance().getReference("/command")
val roomRef = FirebaseDatabase.getInstance().getReference("/gamerooms")
val query = FirebaseFirestore.getInstance()
var dbSorted = HashMap<Int, Quiz>()
const val CHILD_PLAYERSTATUS_KEY = "playerStatus"
const val CHILD_JOINEDUSER_KEY = "joinedUser"
const val CHILD_MESSAGE_KEY = "message"
const val CHILD_PLAYERHP_KEY = "playerHp"
const val CHILD_HOSTNAME_KEY = "hostName"
const val CHILD_TITLE_KEY = "roomTitle"
const val CHILD_ROUND_KEY = "round"
const val CHILD_ROOMSTATUS_KEY = "roomStatus"
const val CHILD_LISTROOMS_KEY = "listrooms"