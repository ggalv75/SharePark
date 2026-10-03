package com.sharepark.data.remote.cloud

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.sharepark.domain.model.Reservation
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

/** Someone the car is shared with. [name] is null for members who joined before names were kept. */
data class VehicleMember(val uid: String, val name: String?, val isOwner: Boolean, val isMe: Boolean)

class SharingException(message: String) : Exception(message)

/**
 * Firestore layout (enforced by firestore.rules at the repo root):
 *
 *   vehicles/{cloudId}                  name, ownerUid, memberUids[], memberNames{uid: name}, createdAt, joinCode
 *   vehicles/{cloudId}/parkings/{key}   one document per parking, written by whoever parked
 *   vehicles/{cloudId}/reservations/{id} a time slot a member booked the car for
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
                "memberNames" to mapOf(user.uid to user.displayName),
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
        publishMyName(cloudId)

        val vehicle = vehicleRef(cloudId).get().await()
        return JoinedVehicle(cloudId = cloudId, name = vehicle.getString("name") ?: "רכב משותף")
    }

    /**
     * Every shared car the signed-in user is a member of — what a fresh install (or a new phone)
     * restores after signing in.
     */
    suspend fun myVehicles(): List<JoinedVehicle> {
        val user = requireUser()
        return db.collection(VEHICLES)
            .whereArrayContains("memberUids", user.uid)
            .get(Source.SERVER)
            .await()
            .documents
            .map { JoinedVehicle(cloudId = it.id, name = it.getString("name") ?: "רכב משותף") }
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

        // Names go first: once out of memberUids the rules no longer let us touch the car.
        runCatching {
            ref.update(FieldPath.of("memberNames", user.uid), FieldValue.delete()).await()
        }
        if (members.all { it == user.uid }) {
            deleteParkings(cloudId, olderThan = Long.MAX_VALUE)
            deleteReservations(cloudId, endedBefore = Long.MAX_VALUE)
            ref.delete().await()
        } else {
            ref.update("memberUids", FieldValue.arrayRemove(user.uid)).await()
        }
    }

    /**
     * Writes the signed-in user's name into the car's member list (if it isn't there already), so
     * the others see who they share it with. Best effort, in its own write: an older deployment of the rules rejects it,
     * and that must not fail the join itself.
     */
    suspend fun publishMyName(cloudId: String) {
        val user = authRepository.currentUserNow() ?: return
        runCatching {
            val ref = vehicleRef(cloudId)
            val current = ref.get().await().get(FieldPath.of("memberNames", user.uid)) as? String
            if (current != user.displayName) {
                ref.update(FieldPath.of("memberNames", user.uid), user.displayName).await()
            }
        }.onFailure { Log.w(TAG, "Could not publish member name for $cloudId", it) }
    }

    /** Live list of the car's members, owner first, then by name. Ends with an empty list on losing access. */
    fun observeMembers(cloudId: String): Flow<List<VehicleMember>> = callbackFlow {
        val me = authRepository.currentUserNow()
        val registration = vehicleRef(cloudId).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                if (error != null) Log.w(TAG, "Member listener for $cloudId stopped", error)
                trySend(emptyList())
                close()
                return@addSnapshotListener
            }
            @Suppress("UNCHECKED_CAST")
            val uids = snapshot.get("memberUids") as? List<String> ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val names = snapshot.get("memberNames") as? Map<String, Any?> ?: emptyMap()
            val ownerUid = snapshot.getString("ownerUid")
            val members = uids.distinct().map { uid ->
                VehicleMember(
                    uid = uid,
                    // Our own name is known locally even before it reaches the cloud.
                    name = (names[uid] as? String)?.takeIf { it.isNotBlank() }
                        ?: me?.displayName?.takeIf { uid == me.uid },
                    isOwner = uid == ownerUid,
                    isMe = uid == me?.uid
                )
            }
            trySend(members.sortedWith(compareByDescending<VehicleMember> { it.isOwner }.thenBy { it.name ?: "￿" }))
        }
        awaitClose { registration.remove() }
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

    // ── Reservations ────────────────────────────────────────────────────────

    /**
     * Books the car for [startAt, endAt). Fails with a [SharingException] naming the member who
     * already holds an overlapping slot. The check runs on this phone, so two members booking the
     * same slot within the same second could both succeed — rare enough for a family car.
     */
    suspend fun addReservation(cloudId: String, startAt: Long, endAt: Long, note: String?): Reservation {
        val user = requireUser()
        if (endAt <= startAt) throw SharingException("שעת הסיום חייבת להיות אחרי שעת ההתחלה")
        if (endAt <= System.currentTimeMillis()) throw SharingException("אי אפשר לשריין זמן שכבר עבר")

        val conflict = reservationsEndingAfter(cloudId, startAt).firstOrNull { it.overlaps(startAt, endAt) }
        if (conflict != null) {
            throw SharingException("הרכב כבר משוריין בזמן הזה ע\"י ${conflict.reservedByName}")
        }

        val ref = vehicleRef(cloudId).collection(RESERVATIONS).document()
        val cleanNote = note?.trim()?.take(NOTE_MAX_LENGTH)?.takeIf { it.isNotEmpty() }
        ref.set(
            mapOf(
                "startAt" to startAt,
                "endAt" to endAt,
                "reservedByUid" to user.uid,
                "reservedByName" to user.displayName,
                "note" to cleanNote,
                "createdAt" to System.currentTimeMillis()
            )
        ).await()
        return Reservation(ref.id, startAt, endAt, user.uid, user.displayName, cleanNote)
    }

    /** Only the member who booked a slot can cancel it (enforced by the rules too). */
    suspend fun cancelReservation(cloudId: String, reservationId: String) {
        requireUser()
        vehicleRef(cloudId).collection(RESERVATIONS).document(reservationId).delete().await()
    }

    /**
     * Live feed of the car's reservations that haven't ended yet, soonest first. With
     * [serverOnly], snapshots answered from the local cache are skipped: a cold cache looks like
     * "no reservations", which must not read as "everything was cancelled".
     */
    fun observeReservations(cloudId: String, serverOnly: Boolean = false): Flow<List<Reservation>> = callbackFlow {
        val registration = vehicleRef(cloudId).collection(RESERVATIONS)
            .whereGreaterThan("endAt", System.currentTimeMillis())
            .orderBy("endAt")
            .limit(OBSERVE_LIMIT)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Reservation listener for $cloudId stopped", error)
                    close()
                    return@addSnapshotListener
                }
                if (serverOnly && snapshot?.metadata?.isFromCache != false) return@addSnapshotListener
                trySend(snapshot?.documents.orEmpty().mapNotNull { it.toReservation() }.sortedBy { it.startAt })
            }
        awaitClose { registration.remove() }
    }

    /**
     * One-shot read of the car's reservations that haven't ended, straight from the server — for
     * the background check that runs while no listener is alive.
     */
    suspend fun upcomingReservations(cloudId: String): List<Reservation> =
        vehicleRef(cloudId).collection(RESERVATIONS)
            .whereGreaterThan("endAt", System.currentTimeMillis())
            .get(Source.SERVER)
            .await()
            .documents
            .mapNotNull { it.toReservation() }

    /** Removes slots that ended before [endedBefore] — same 30-day window as the parkings. */
    suspend fun deleteReservations(cloudId: String, endedBefore: Long) {
        val stale = vehicleRef(cloudId).collection(RESERVATIONS)
            .whereLessThan("endAt", endedBefore)
            .get()
            .await()
        stale.documents.chunked(BATCH_LIMIT).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    private suspend fun reservationsEndingAfter(cloudId: String, time: Long): List<Reservation> =
        vehicleRef(cloudId).collection(RESERVATIONS)
            .whereGreaterThan("endAt", time)
            .get()
            .await()
            .documents
            .mapNotNull { it.toReservation() }

    private fun DocumentSnapshot.toReservation(): Reservation? {
        val startAt = getLong("startAt") ?: return null
        val endAt = getLong("endAt") ?: return null
        return Reservation(
            id = id,
            startAt = startAt,
            endAt = endAt,
            reservedByUid = getString("reservedByUid").orEmpty(),
            reservedByName = getString("reservedByName").orEmpty(),
            note = getString("note")
        )
    }

    private fun generateCode(): String =
        (1..CODE_LENGTH).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }.joinToString("")

    companion object {
        private const val TAG = "SharedVehicles"
        private const val VEHICLES = "vehicles"
        private const val PARKINGS = "parkings"
        private const val INVITES = "invites"
        private const val RESERVATIONS = "reservations"
        private const val NOTE_MAX_LENGTH = 200
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
