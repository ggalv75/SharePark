package com.sharepark.data.remote.cloud

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** A parking as stored in a shared vehicle's cloud history. */
data class CloudParking(
    val key: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val address: String?,
    val parkedAt: Long,
    val parkedByUid: String,
    val parkedByName: String
)

data class JoinedVehicle(val cloudId: String, val name: String)

class SharingException(message: String) : Exception(message)

/**
 * Firestore layout (enforced by firestore.rules at the repo root):
 *
 *   vehicles/{cloudId}                  name, ownerUid, memberUids[], createdAt, joinCode
 *   vehicles/{cloudId}/parkings/{key}   one document per parking, written by whoever parked
 *   invites/{code}                      vehicleId, createdBy, expiresAt — knowing the code is the grant
 *
 * Joining needs no server code: the joiner adds only their own uid to memberUids and names the
 * invite in the same write, and the rules check that invite exists, matches, and hasn't expired.
 */
@Singleton
class SharedVehicleRepository @Inject constructor(
    private val authRepository: AuthRepository
) {
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private fun requireUser(): CloudUser =
        authRepository.currentUserNow() ?: throw SharingException("יש להתחבר עם חשבון Google קודם")

    private fun vehicleRef(cloudId: String) = db.collection(VEHICLES).document(cloudId)

    /** Creates the cloud side of a car so it can be shared. Returns its cloud id. */
    suspend fun createSharedVehicle(name: String): String {
        val user = requireUser()
        val ref = db.collection(VEHICLES).document()
        ref.set(
            mapOf(
                "name" to name,
                "ownerUid" to user.uid,
                "memberUids" to listOf(user.uid),
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        return ref.id
    }

    /** A short code another person types in to join; valid for [INVITE_TTL_HOURS]. */
    suspend fun createInvite(cloudId: String): String {
        val user = requireUser()
        val code = generateCode()
        val expiresAt = Timestamp(Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(INVITE_TTL_HOURS)))
        db.collection(INVITES).document(code).set(
            mapOf(
                "vehicleId" to cloudId,
                "createdBy" to user.uid,
                "expiresAt" to expiresAt
            )
        ).await()
        return code
    }

    suspend fun joinWithCode(rawCode: String): JoinedVehicle {
        val user = requireUser()
        val code = normalizeCode(rawCode)
        if (code.length != CODE_LENGTH) throw SharingException("הקוד צריך להכיל $CODE_LENGTH תווים")

        val invite = db.collection(INVITES).document(code).get().await()
        if (!invite.exists()) throw SharingException("הקוד לא נמצא — בדוק שהוקלד נכון")
        val expiresAt = invite.getTimestamp("expiresAt")
        if (expiresAt == null || expiresAt.toDate().time < System.currentTimeMillis()) {
            throw SharingException("תוקף ההזמנה פג — בקש קוד חדש")
        }
        val cloudId = invite.getString("vehicleId") ?: throw SharingException("ההזמנה פגומה")

        vehicleRef(cloudId).update(
            mapOf(
                "memberUids" to FieldValue.arrayUnion(user.uid),
                "joinCode" to code
            )
        ).await()

        val vehicle = vehicleRef(cloudId).get().await()
        return JoinedVehicle(cloudId = cloudId, name = vehicle.getString("name") ?: "רכב משותף")
    }

    /**
     * Stops sharing this car with the current user. The last member out deletes the vehicle
     * and its history, so nothing is left behind in the cloud.
     */
    suspend fun leave(cloudId: String) {
        val user = requireUser()
        val ref = vehicleRef(cloudId)
        val snapshot = ref.get().await()
        if (!snapshot.exists()) return
        @Suppress("UNCHECKED_CAST")
        val members = snapshot.get("memberUids") as? List<String> ?: emptyList()

        if (members.all { it == user.uid }) {
            deleteParkings(cloudId, olderThan = Long.MAX_VALUE)
            ref.delete().await()
        } else {
            ref.update("memberUids", FieldValue.arrayRemove(user.uid)).await()
        }
    }

    suspend fun uploadParking(cloudId: String, parking: CloudParking) {
        vehicleRef(cloudId).collection(PARKINGS).document(parking.key).set(
            mapOf(
                "latitude" to parking.latitude,
                "longitude" to parking.longitude,
                "accuracy" to parking.accuracy.toDouble(),
                "address" to parking.address,
                "parkedAt" to parking.parkedAt,
                "parkedByUid" to parking.parkedByUid,
                "parkedByName" to parking.parkedByName
            )
        ).await()
    }

    /**
     * Live feed of the car's recent parkings. Ends quietly on an error such as losing access
     * (another member can't remove you, but the vehicle can be deleted by its last member).
     */
    fun observeParkings(cloudId: String, sinceMillis: Long): Flow<List<CloudParking>> = callbackFlow {
        val registration = vehicleRef(cloudId).collection(PARKINGS)
            .whereGreaterThanOrEqualTo("parkedAt", sinceMillis)
            .orderBy("parkedAt", Query.Direction.DESCENDING)
            .limit(OBSERVE_LIMIT)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Parking listener for $cloudId stopped", error)
                    close()
                    return@addSnapshotListener
                }
                val parkings = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    val latitude = doc.getDouble("latitude") ?: return@mapNotNull null
                    val longitude = doc.getDouble("longitude") ?: return@mapNotNull null
                    CloudParking(
                        key = doc.id,
                        latitude = latitude,
                        longitude = longitude,
                        accuracy = doc.getDouble("accuracy")?.toFloat() ?: 0f,
                        address = doc.getString("address"),
                        parkedAt = doc.getLong("parkedAt") ?: return@mapNotNull null,
                        parkedByUid = doc.getString("parkedByUid").orEmpty(),
                        parkedByName = doc.getString("parkedByName").orEmpty()
                    )
                }
                trySend(parkings)
            }
        awaitClose { registration.remove() }
    }

    /** Keeps the cloud history to the same 30 days as the local one. */
    suspend fun deleteParkings(cloudId: String, olderThan: Long) {
        val stale = vehicleRef(cloudId).collection(PARKINGS)
            .whereLessThan("parkedAt", olderThan)
            .get()
            .await()
        stale.documents.chunked(BATCH_LIMIT).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    private fun generateCode(): String =
        (1..CODE_LENGTH).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }.joinToString("")

    companion object {
        private const val TAG = "SharedVehicles"
        private const val VEHICLES = "vehicles"
        private const val PARKINGS = "parkings"
        private const val INVITES = "invites"
        private const val OBSERVE_LIMIT = 50L
        private const val BATCH_LIMIT = 400
        private const val INVITE_TTL_HOURS = 48L

        const val CODE_LENGTH = 8

        // No 0/O, 1/I/L — the code is read aloud and typed on a phone.
        private const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        private val random = SecureRandom()

        fun normalizeCode(raw: String): String =
            raw.uppercase().filter { it.isLetterOrDigit() }
    }
}
