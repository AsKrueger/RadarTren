package com.askrueger.radartren

import com.askrueger.radartren.domain.CancelledTrip
import com.askrueger.radartren.domain.Core
import com.askrueger.radartren.domain.IncidentCategory
import com.askrueger.radartren.domain.IncidentDetector
import com.askrueger.radartren.domain.NetworkSnapshot
import com.askrueger.radartren.domain.Service
import com.askrueger.radartren.domain.Severity
import com.askrueger.radartren.domain.Train
import com.askrueger.radartren.domain.TrainStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentDetectorTest {
    private fun train(number: Int, delay: Int?, core: Core? = Core.MADRID) = Train(
        id = "t$number", service = if (core == null) Service.LONG_DISTANCE else Service.COMMUTER,
        label = "C1", number = "$number", latitude = 40.0, longitude = -3.0, status = TrainStatus.MOVING,
        delayMinutes = delay, core = core, nextStation = null, origin = null, destination = null,
    )

    private fun cancelled(count: Int, core: Core?) = List(count) {
        CancelledTrip("c$it", if (core == null) Service.LONG_DISTANCE else Service.COMMUTER, core)
    }

    private fun detect(trains: List<Train>, cancelled: List<CancelledTrip> = emptyList()) =
        IncidentDetector.detect(NetworkSnapshot(trains, cancelled, emptyList()), now = 1)

    @Test
    fun `single train delays scale with their size`() {
        val incidents = detect(listOf(train(1, 29), train(2, 30), train(3, 119), train(4, 120)))
        assertEquals(listOf(Severity.MEDIUM, Severity.MEDIUM, Severity.HIGH), incidents.map { it.severity })
    }

    @Test
    fun `a whole network running late is one incident`() {
        val trains = List(6) { train(it, 16) } + List(4) { train(it + 10, 0) }
        val area = detect(trains).single { it.category == IncidentCategory.DISRUPTION }
        assertEquals(Severity.HIGH, area.severity)
        assertEquals("6/10", area.text)
    }

    @Test
    fun `a small network never reaches a percentage alarm`() {
        assertTrue(detect(List(7) { train(it, 20) }).isEmpty())
    }

    @Test
    fun `cancellations are judged against the size of the network`() {
        val ordinaryLongDistance = detect(List(147) { train(it, 0, core = null) }, cancelled(12, null))
        assertTrue(ordinaryLongDistance.isEmpty())

        val smallCore = detect(List(9) { train(it, 0, Core.SANTANDER) }, cancelled(3, Core.SANTANDER))
        assertEquals(Severity.HIGH, smallCore.single().severity)

        val mediumCore = detect(List(20) { train(it, 0) }, cancelled(3, Core.MADRID))
        assertEquals(Severity.MEDIUM, mediumCore.single().severity)
    }

    @Test
    fun `the same train keeps one incident across trip dates`() {
        val keys = listOf(train(190, 130, core = null), train(190, 131, core = null).copy(id = "other"))
            .let { detect(it) }.map { it.key }.toSet()
        assertEquals(1, keys.size)
    }

    @Test
    fun `incidents carry where to draw them`() {
        val train = detect(listOf(train(1, 130).copy(latitude = 41.5, longitude = 2.1))).single()
        assertEquals(41.5, train.latitude!!, 0.0)

        val area = detect(List(10) { train(it, 20) }).single { it.category == IncidentCategory.DISRUPTION }
        assertEquals(Core.MADRID.latitude, area.latitude!!, 0.0)

        val longDistance = detect(List(10) { train(it, 20, core = null) }).single { it.category == IncidentCategory.DISRUPTION }
        assertEquals(null, longDistance.latitude)
    }

    @Test
    fun `commuter trains of an unknown network are not counted as long distance`() {
        val unknown = List(10) { train(it, 40, core = null).copy(service = Service.COMMUTER) }
        val onTime = List(10) { train(it + 20, 0, core = null) }
        assertTrue(detect(unknown + onTime).none { it.category == IncidentCategory.DISRUPTION })
    }
}
