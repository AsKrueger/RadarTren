package com.askrueger.radartren

import com.askrueger.radartren.data.remote.FeedMessage
import com.askrueger.radartren.domain.AlertClassifier
import com.askrueger.radartren.domain.DelayLevel
import com.askrueger.radartren.domain.Feed
import com.askrueger.radartren.domain.FeedHealth
import com.askrueger.radartren.domain.FeedState
import com.askrueger.radartren.domain.IncidentDetector
import com.askrueger.radartren.domain.Service
import com.askrueger.radartren.domain.Severity
import com.askrueger.radartren.domain.SnapshotAssembler
import com.askrueger.radartren.domain.StaticCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Runs against real Renfe captures from 2026-11-01 kept in src/test/resources/feeds. */
class FeedParsingTest {
    private fun feed(name: String) = FeedMessage.decode(
        requireNotNull(javaClass.classLoader!!.getResourceAsStream("feeds/$name.pb")) { name }.readBytes(),
    )

    private val catalog by lazy {
        val dir = File("src/main/assets/catalog")
        StaticCatalog.parse(
            dir.resolve("stations.csv").readLines().asSequence(),
            dir.resolve("lines.csv").readLines().asSequence(),
            dir.resolve("trains.csv").readLines().asSequence(),
        )
    }

    private fun snapshot() = SnapshotAssembler(catalog).assemble(
        feed("vehicle_positions"), feed("vehicle_positions_LD"),
        feed("trip_updates"), feed("trip_updates_LD"), emptyList(),
    )

    @Test
    fun `decodes every position with coordinates inside Spain and nearby`() {
        val trains = snapshot().trains
        assertTrue(trains.size > 300)
        assertTrue(trains.all { it.latitude in 27.0..49.0 && it.longitude in -19.0..5.0 })
        assertTrue(trains.any { it.service == Service.COMMUTER })
        assertTrue(trains.any { it.service == Service.LONG_DISTANCE })
    }

    @Test
    fun `joins delays and catalog names onto trains`() {
        val trains = snapshot().trains
        val known = trains.count { it.delayMinutes != null }
        assertTrue("only $known of ${trains.size} have a delay", known > trains.size * 0.8)
        assertTrue(trains.count { it.nextStation != null } > trains.size * 0.7)
        val longDistance = trains.filter { it.service == Service.LONG_DISTANCE }
        assertTrue(longDistance.count { it.label != null } > longDistance.size * 0.8)
    }

    @Test
    fun `discards implausible delays and detects cancellations`() {
        val snapshot = snapshot()
        assertTrue(snapshot.trains.mapNotNull { it.delayMinutes }.all { it in -360..360 })
        assertTrue(snapshot.cancelled.isNotEmpty())
    }

    @Test
    fun `alerts decode, drop the test notice and merge repeated notices`() {
        val message = feed("alerts")
        assertEquals(69, message.entity.size)
        val drafts = IncidentDetector.officialAlerts(message, catalog)
        assertEquals(67, drafts.size)
        assertTrue(drafts.none { it.text.startsWith("Pruebas") })

        val copies = message.entity.filter { it.id == "AVISO_518187" }
        assertEquals(2, copies.size)
        val routes = copies.flatMap { it.alert!!.informedEntity }.mapNotNull { catalog.line(it.routeId)?.name }.distinct()
        assertEquals(routes.toSet(), drafts.single { it.key.startsWith("alert:AVISO_518187:") }.lines.toSet())
    }

    @Test
    fun `every real notice gets a sensible class`() {
        val drafts = IncidentDetector.officialAlerts(feed("alerts"), catalog)
        assertTrue("located: ${drafts.count { it.latitude != null }}", drafts.count { it.latitude != null } > drafts.size * 0.9)
        assertTrue(drafts.none { it.severity == Severity.CRITICAL })
        assertTrue(drafts.count { it.severity == Severity.HIGH } in 1..12)
        val lifts = drafts.filter { "scensor" in it.text }
        assertTrue(lifts.all { it.severity == Severity.LOW })
    }

    @Test
    fun `basque notices keep the spanish half`() {
        val raw = "ANDOAIN: 2. trenbideetako igogailua zerbitzuz kanpo dago. // El ascensor de vía 2 se encuentra fuera de servicio."
        assertEquals("El ascensor de vía 2 se encuentra fuera de servicio.", AlertClassifier.readableText(raw))
    }

    @Test
    fun `a normal day raises no critical incident from delays`() {
        val detected = IncidentDetector.detect(snapshot(), now = 1_000)
        assertTrue(detected.none { it.severity == Severity.CRITICAL })
        assertTrue("${detected.size}", detected.count { it.severity == Severity.HIGH } < 15)
    }

    @Test
    fun `health uses server headers so a frozen feed is caught`() {
        val serverNow = 1_793_526_000L
        val frozen = FeedHealth.evaluate(Feed.ALERTS, lastModified = serverNow - 25 * 86_400, serverNow = serverNow)
        assertEquals(FeedState.FROZEN, frozen.state)
        val live = FeedHealth.evaluate(Feed.COMMUTER_POSITIONS, lastModified = serverNow - 20, serverNow = serverNow)
        assertEquals(FeedState.LIVE, live.state)
        assertFalse(FeedHealth.evaluate(Feed.ALERTS, null, serverNow).state == FeedState.LIVE)
    }

    @Test
    fun `delay levels`() {
        assertEquals(DelayLevel.UNKNOWN, DelayLevel.of(null))
        assertEquals(DelayLevel.ON_TIME, DelayLevel.of(-3))
        assertEquals(DelayLevel.MINOR, DelayLevel.of(5))
        assertEquals(DelayLevel.MODERATE, DelayLevel.of(15))
        assertEquals(DelayLevel.SEVERE, DelayLevel.of(30))
    }
}
