package com.sidekick.opt_pal.core.session

import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

interface UserSessionProvider {
    val currentUserId: String?
}

object FirebaseUserSessionProvider : UserSessionProvider {
    override val currentUserId: String?
        get() = Firebase.auth.currentUser?.uid
}
