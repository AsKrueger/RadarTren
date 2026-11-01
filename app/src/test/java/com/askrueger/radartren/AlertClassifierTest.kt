package com.askrueger.radartren

import com.askrueger.radartren.domain.AlertClassifier
import com.askrueger.radartren.domain.IncidentCategory
import com.askrueger.radartren.domain.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlertClassifierTest {
    private fun check(text: String, severity: Severity, category: IncidentCategory) {
        val result = AlertClassifier.classify(text)!!
        assertEquals(text, severity, result.severity)
        assertEquals(text, category, result.category)
    }

    @Test
    fun `test notices are dropped`() {
        assertNull(AlertClassifier.classify("Pruebas, disculpen las molestias"))
        assertNull(AlertClassifier.classify("   "))
    }

    @Test
    fun `safety words are critical and survive accents and case`() {
        check("Descarrilamiento de un tren en Atocha", Severity.CRITICAL, IncidentCategory.SAFETY)
        check("ARROLLAMIENTO DE UNA PERSONA", Severity.CRITICAL, IncidentCategory.SAFETY)
        check("Colisión entre dos trenes", Severity.CRITICAL, IncidentCategory.SAFETY)
        check("Accidente durante las obras", Severity.CRITICAL, IncidentCategory.SAFETY)
    }

    @Test
    fun `interruptions are high`() {
        check("Sin servicio ferroviario.", Severity.HIGH, IncidentCategory.SERVICE_INTERRUPTED)
        check(
            "Por causas ajenas a Rodalies no se puede garantizar la prestación del servicio.",
            Severity.HIGH, IncidentCategory.SERVICE_INTERRUPTED,
        )
        check(
            "Debido a falta de suministro eléctrico se interrumpe el servicio",
            Severity.HIGH, IncidentCategory.SERVICE_INTERRUPTED,
        )
    }

    @Test
    fun `operational problems are medium`() {
        check("Circulación por vía única entre Sant Vicenç y Cunit", Severity.MEDIUM, IncidentCategory.DISRUPTION)
        check("Efectúa su salida con 17 minutos de retraso.", Severity.MEDIUM, IncidentCategory.DISRUPTION)
    }

    @Test
    fun `scheduled works are downgraded even with serious wording`() {
        check(
            "Por obras de reforma integral en la línea C-9, se encuentra suspendido el servicio de trenes",
            Severity.LOW, IncidentCategory.PLANNED_WORKS,
        )
        check("SERVICIO POR AUTOBÚS C-5: Plan alternativo de transporte", Severity.LOW, IncidentCategory.PLANNED_WORKS)
    }

    @Test
    fun `recoveries and normal service do not alarm`() {
        check("Los trenes recuperan sus frecuencias de paso habituales", Severity.INFO, IncidentCategory.INFORMATION)
        check("Circulación ferroviaria en todo su recorrido.", Severity.INFO, IncidentCategory.INFORMATION)
    }

    @Test
    fun `lifts are accessibility notices`() {
        check("Ascensor de la estación de Coslada fuera de servicio", Severity.LOW, IncidentCategory.ACCESSIBILITY)
    }
}
