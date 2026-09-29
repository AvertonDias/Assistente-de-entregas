package com.example.ui.screens.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.AppActionLog
import com.example.data.local.entity.DailyActivitySummary
import com.example.data.repository.ActivityReportRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DailyReportViewModel(
    private val repository: ActivityReportRepository
) : ViewModel() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private val _selectedDateKey = MutableStateFlow(repository.getTodayDateKey())
    val selectedDateKey: StateFlow<String> = _selectedDateKey.asStateFlow()

    private val _selectedCategory = MutableStateFlow("Todas")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    val availableDates: StateFlow<List<String>> = repository.getAvailableDates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val dailySummary: StateFlow<DailyActivitySummary?> = _selectedDateKey
        .flatMapLatest { key -> repository.getDailySummary(key) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val rawActions: StateFlow<List<AppActionLog>> = _selectedDateKey
        .flatMapLatest { key -> repository.getActionsByDate(key) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredActions: StateFlow<List<AppActionLog>> = combine(rawActions, _selectedCategory) { actions, category ->
        if (category == "Todas") {
            actions
        } else {
            actions.filter { it.category.equals(category, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectDate(dateKey: String) {
        _selectedDateKey.value = dateKey
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun isTodaySelected(): Boolean {
        return _selectedDateKey.value == repository.getTodayDateKey()
    }

    fun navigateDay(offset: Int) {
        try {
            val date = dateFormat.parse(_selectedDateKey.value) ?: Date()
            val cal = Calendar.getInstance().apply {
                time = date
                add(Calendar.DAY_OF_YEAR, offset)
            }
            _selectedDateKey.value = dateFormat.format(cal.time)
        } catch (_: Exception) {}
    }

    fun selectToday() {
        _selectedDateKey.value = repository.getTodayDateKey()
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistoryForDate(_selectedDateKey.value)
        }
    }

    suspend fun getExportText(): String {
        return repository.buildDailyReportExportText(_selectedDateKey.value)
    }

    class Factory(
        private val repository: ActivityReportRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DailyReportViewModel::class.java)) {
                return DailyReportViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
