package com.example.vacuumcontroller

import java.util.regex.Pattern

data class VacuumState(
    var stato: Int = 0,      // 0 = spento, 1 = acceso
    var livello: Int = 1,    // 1-4 livelli di potenza
    var potenza: Int = 0,    // potenza in watt
    var sensore: Int = 0     // valore sensore
) {
    
    companion object {
        fun parseFromMessage(message: String): VacuumState? {
            return try {
                if (message.contains("\"type\":\"status\"")) {
                    val state = VacuumState()
                    
                    // Parsing usando regex come nel Flutter
                    val statoMatch = Pattern.compile("\"stato\":(\\d+)").matcher(message)
                    val livelloMatch = Pattern.compile("\"livello\":(\\d+)").matcher(message)
                    val potenzaMatch = Pattern.compile("\"potenza\":(\\d+)").matcher(message)
                    val sensoreMatch = Pattern.compile("\"sensore\":(\\d+)").matcher(message)
                    
                    if (statoMatch.find()) {
                        state.stato = statoMatch.group(1)?.toInt() ?: 0
                    }
                    if (livelloMatch.find()) {
                        state.livello = livelloMatch.group(1)?.toInt() ?: 1
                    }
                    if (potenzaMatch.find()) {
                        state.potenza = potenzaMatch.group(1)?.toInt() ?: 0
                    }
                    if (sensoreMatch.find()) {
                        state.sensore = sensoreMatch.group(1)?.toInt() ?: 0
                    }
                    
                    state
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    
    fun isOn(): Boolean = stato == 1
    
    fun canIncrease(): Boolean = isOn() && livello < 4
    
    fun canDecrease(): Boolean = isOn() && livello > 1
    
    fun getStatusText(): String = if (isOn()) "Accesa" else "Spenta"
    
    fun getStatusColor(): Int = if (isOn()) 
        android.graphics.Color.parseColor("#4CAF50") 
    else 
        android.graphics.Color.parseColor("#F44336")
}