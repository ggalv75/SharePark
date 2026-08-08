package com.sharepark.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharepark.data.repository.TrustedContactRepository
import com.sharepark.domain.model.TrustedContact
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrustedContactsViewModel @Inject constructor(
    private val trustedContactRepository: TrustedContactRepository
) : ViewModel() {

    val contacts: StateFlow<List<TrustedContact>> = trustedContactRepository.allContacts
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun addContact(name: String, phoneNumber: String) {
        val trimmedName = name.trim()
        val trimmedPhone = phoneNumber.trim()
        if (trimmedName.isEmpty() || trimmedPhone.isEmpty()) return
        viewModelScope.launch {
            trustedContactRepository.addContact(trimmedName, trimmedPhone)
        }
    }

    fun deleteContact(contact: TrustedContact) {
        viewModelScope.launch {
            trustedContactRepository.deleteContact(contact)
        }
    }
}
