package com.noobdev.waffle.data.local

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.noobdev.waffle.data.model.DisasterAlert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AlertRepository(private val alertDao: AlertDao, private val context: android.content.Context) {

    private val db = FirebaseFirestore.getInstance()
    private var realTimeListener: ListenerRegistration? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val notificationHelper = com.noobdev.waffle.core.notifications.NotificationHelper(context)
    private var isFirstLoad = true

    // Local flows
    val allAlertsFlow: Flow<List<DisasterAlert>> = alertDao.getAllAlertsFlow().map { entities ->
        entities.map { it.toDomainModel() }
    }

    fun getAlertsByType(type: String): Flow<List<DisasterAlert>> {
        return alertDao.getAlertsByTypeFlow(type).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    suspend fun syncAlertsFromNetwork() {
        try {
            // Wait for anonymous sign-in before querying, but continue if it fails
            try {
                FirebaseAuth.getInstance().signInAnonymously().await()
            } catch (authEx: Exception) {
                Log.e("AlertRepository", "Anonymous Auth failed but continuing", authEx)
            }
            val snapshot = db.collection("disaster_alerts")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(100) // Start with recent 100 alerts
                .get()
                .await()

            val entities = snapshot.documents.mapNotNull { doc ->
                try {
                    val alert = doc.toObject(DisasterAlert::class.java)
                    if (alert?.title?.contains("[OSINT AI ERROR]") == true) {
                        null
                    } else {
                        // Firestore documents commonly omit the id field; use the document id
                        // so the local primary key is never null and removals can be matched.
                        alert?.copy(id = alert.id ?: doc.id)?.toEntity()
                    }
                } catch (e: Exception) {
                    null
                }
            }
            if (entities.isNotEmpty()) {
                alertDao.insertAll(entities)
            }
        } catch (e: Exception) {
            Log.e("AlertRepository", "Failed to sync initial alerts", e)
        }
    }

    // Set up real-time listener for new items globally in the background
    fun startRealTimeSync() {
        if (realTimeListener != null) return

        realTimeListener = db.collection("disaster_alerts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50) // only listen to latest changes to avoid huge payloads
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("AlertRepository", "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val entitiesToInsert = mutableListOf<AlertEntity>()
                    val idsToDelete = mutableListOf<String>()

                    for (change in snapshots.documentChanges) {
                        val doc = change.document
                        try {
                            val alert = doc.toObject(DisasterAlert::class.java)
                            if (alert?.title?.contains("[OSINT AI ERROR]") == true) continue
                            val normalized = alert?.copy(id = alert.id ?: doc.id) ?: continue
                            val entity = normalized.toEntity()

                            when (change.type) {
                                DocumentChange.Type.ADDED -> {
                                    if (entity != null) entitiesToInsert.add(entity)
                                    // Trigger notification for ALL alerts if it's not the initial load of 50 items
                                    if (!isFirstLoad) {
                                        notificationHelper.showDisasterAlertNotification(normalized)
                                    }
                                }
                                DocumentChange.Type.MODIFIED -> {
                                    if (entity != null) entitiesToInsert.add(entity)
                                }
                                DocumentChange.Type.REMOVED -> {
                                    idsToDelete.add(doc.id)
                                }
                            }
                        } catch (e: Exception) {
                            // ignore doc parsing error
                        }
                    }

                    isFirstLoad = false

                    scope.launch {
                        if (entitiesToInsert.isNotEmpty()) alertDao.insertAll(entitiesToInsert)
                        for (id in idsToDelete) {
                            alertDao.deleteAlertById(id)
                        }
                    }
                }
            }
    }

    fun stopRealTimeSync() {
        realTimeListener?.remove()
        realTimeListener = null
    }
}
