package com.askrueger.radartren

import com.askrueger.radartren.domain.NetworkStats
import com.askrueger.radartren.domain.ProductFamily
import com.askrueger.radartren.domain.Service
import com.askrueger.radartren.domain.Train
import com.askrueger.radartren.domain.TrainStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkStatsTest {
    private fun train(delay: Int?) = Train(
        "t", Service.COMMUTER, "C1", "1", 40.0, -3.0, TrainStatus.MOVING, delay, null, null, null, null,
    )

    @Test
    fun `every Renfe product falls in its family`() {
        val expected = mapOf(
            "AVE" to ProductFamily.AVE, "AVE INT" to ProductFamily.AVE, "AVLO" to ProductFamily.AVLO,
            "AVANT" to ProductFamily.AVANT, "AVANT EXP" to ProductFamily.AVANT, "ALVIA" to ProductFamily.ALVIA,
            "EUROMED" to ProductFamily.EUROMED, "Intercity" to ProductFamily.INTERCITY, "MD" to ProductFamily.MEDIA_DISTANCIA,
            "REGIONAL" to ProductFamily.REGIONAL, "REG.EXP." to ProductFamily.REGIONAL, "PROXIMDAD" to ProductFamily.PROXIMIDAD,
            "TRENCELTA" to ProductFamily.OTHER, null to ProductFamily.OTHER,
        )
        expected.forEach { (product, family) -> assertEquals("$product", family, ProductFamily.ofProduct(product)) }
    }

    @Test
    fun `commuter trains are one family whatever their line`() {
        assertEquals(ProductFamily.COMMUTER, train(0).family)
    }

    @Test
    fun `stats ignore trains without delay data and early arrivals in the average`() {
        val stats = NetworkStats.of(listOf(train(10), train(-4), train(0), train(null), train(20)))
        assertEquals(5, stats.onMap)
        assertEquals(2, stats.delayed)
        assertEquals(8, stats.averageDelayMinutes)
    }

    @Test
    fun `no trains gives zeros`() {
        assertEquals(NetworkStats(0, 0, 0), NetworkStats.of(emptyList()))
    }
}
