package com.sharepark.data.repository

import com.sharepark.data.local.dao.TrustedContactDao
import com.sharepark.data.local.entity.TrustedContactEntity
import com.sharepark.domain.model.TrustedContact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrustedContactRepository @Inject constructor(
    private val trustedContactDao: TrustedContactDao
) {
    val allContacts: Flow<List<TrustedContact>> = trustedContactDao.getAllContacts().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun addContact(name: String, phoneNumber: String) {
        trustedContactDao.insert(TrustedContactEntity(name = name, phoneNumber = phoneNumber))
    }

    suspend fun deleteContact(contact: TrustedContact) {
        trustedContactDao.delete(
            TrustedContactEntity(id = contact.id, name = contact.name, phoneNumber = contact.phoneNumber, createdAt = contact.createdAt)
        )
    }

    private fun TrustedContactEntity.toDomain() = TrustedContact(
        id = id,
        name = name,
        phoneNumber = phoneNumber,
        createdAt = createdAt
    )
}
