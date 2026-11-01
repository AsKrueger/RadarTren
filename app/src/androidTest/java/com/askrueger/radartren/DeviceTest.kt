package com.askrueger.radartren

import android.content.Context
import android.content.res.Configuration
import android.graphics.pdf.PdfRenderer
import android.os.LocaleList
import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.askrueger.radartren.data.IncidentRepository
import com.askrueger.radartren.data.LiveData
import com.askrueger.radartren.data.local.MetricSampleEntity
import com.askrueger.radartren.data.local.RadarDatabase
import com.askrueger.radartren.data.remote.Alert
import com.askrueger.radartren.data.remote.FeedEntity
import com.askrueger.radartren.data.remote.FeedMessage
import com.askrueger.radartren.data.remote.TranslatedString
import com.askrueger.radartren.data.remote.Translation
import com.askrueger.radartren.domain.Core
import com.askrueger.radartren.domain.Feed
import com.askrueger.radartren.domain.FeedHealth
import com.askrueger.radartren.domain.FeedState
import com.askrueger.radartren.domain.IncidentCategory
import com.askrueger.radartren.domain.IncidentSource
import com.askrueger.radartren.domain.MetricSample
import com.askrueger.radartren.domain.NetworkSnapshot
import com.askrueger.radartren.domain.PeriodType
import com.askrueger.radartren.domain.ReportBuilder
import com.askrueger.radartren.domain.ReportIncident
import com.askrueger.radartren.domain.ReportPeriod
import com.askrueger.radartren.domain.SPAIN_ZONE
import com.askrueger.radartren.domain.Service
import com.askrueger.radartren.domain.Severity
import com.askrueger.radartren.domain.StaticCatalog
import com.askrueger.radartren.domain.Train
import com.askrueger.radartren.domain.TrainStatus
import com.askrueger.radartren.report.PdfReportWriter
import com.askrueger.radartren.work.CollectWorker
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class DeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, RadarDatabase::class.java).build()
    private val repository = IncidentRepository(database, StaticCatalog.EMPTY)

    @After
    fun close() = database.close()

    private fun health(alerts: FeedState) = Feed.entries.map { feed ->
        FeedHealth(feed, if (feed == Feed.ALERTS) alerts else FeedState.LIVE, 1)
    }

    private fun alerts(text: String) = FeedMessage(
        entity = listOf(
            FeedEntity(
                id = "AVISO_1",
                alert = Alert(descriptionText = TranslatedString(listOf(Translation(text, "es")))),
            ),
        ),
    )

    private fun train(delay: Int) = Train(
        "t1", Service.COMMUTER, "C1", "1", 40.0, -3.0, TrainStatus.MOVING, delay, Core.MADRID, null, null, null,
    )

    private fun live(alertState: FeedState, alerts: FeedMessage?, trains: List<Train> = emptyList()) =
        LiveData(NetworkSnapshot(trains, emptyList(), health(alertState)), alerts, 0)

    @Test
    fun frozenFeedIsStoredButNeverAnnouncedOrVerified() = runBlocking {
        val announced = repository.record(live(FeedState.FROZEN, alerts("Sin servicio ferroviario.")), 1000)
        assertTrue(announced.isEmpty())
        assertFalse(database.incidents().open().single().verified)
    }

    @Test
    fun liveFeedAnnouncesOnceAndResolvesWhenTheNoticeGoes() = runBlocking {
        val notice = alerts("Sin servicio ferroviario.")
        assertEquals(1, repository.record(live(FeedState.LIVE, notice), 1000).size)
        assertEquals(0, repository.record(live(FeedState.LIVE, notice), 1030).size)
        repository.record(live(FeedState.LIVE, FeedMessage()), 1060)
        assertTrue(database.incidents().open().isEmpty())
    }

    @Test
    fun failedFetchDoesNotResolveAnything() = runBlocking {
        repository.record(live(FeedState.LIVE, alerts("Sin servicio ferroviario.")), 1000)
        repository.record(live(FeedState.UNREACHABLE, null), 1030)
        assertEquals(1, database.incidents().open().size)
    }

    @Test
    fun worseningIsAnnouncedAgain() = runBlocking {
        assertEquals(Severity.MEDIUM, repository.record(live(FeedState.LIVE, null, listOf(train(40))), 1000).single().severity)
        assertEquals(Severity.HIGH, repository.record(live(FeedState.LIVE, null, listOf(train(130))), 1030).single().severity)
    }

    @Test
    fun samplesKeepTheirPaceHoweverOftenTheAppRefreshes() = runBlocking {
        listOf(10_000L, 10_030L, 10_060L, 10_900L).forEach { repository.record(live(FeedState.LIVE, null), it) }
        assertEquals(2, database.metrics().between(0, 20_000).size)
    }

    @Test
    fun pdfIsWrittenInBothLanguages() {
        listOf("es", "en").forEach { language ->
            val configuration = Configuration(context.resources.configuration)
            configuration.setLocales(LocaleList(Locale.forLanguageTag(language)))
            val file = File(context.getExternalFilesDir(null), "muestra-$language.pdf")
            PdfReportWriter(context.createConfigurationContext(configuration)).write(sample(), file)
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { assertTrue(it.pageCount >= 1) }
            }
        }
    }

    private fun sample(): com.askrueger.radartren.domain.ReportData {
        val period = ReportPeriod(PeriodType.WEEK, LocalDate.of(2026, 10, 19))
        val samples = List(520) { index ->
            MetricSample(period.startEpoch + index * 900L, 400, 380, 60 + index % 40, 14, 900, 105, 12)
        }
        val incidents = List(24) { index ->
            ReportIncident(
                Severity.entries[index % 5], IncidentCategory.DISRUPTION, IncidentSource.OFFICIAL,
                Core.entries[index % 4],
                "Circulación por vía única entre Sant Vicenç de Calders y Cunit por una incidencia en la infraestructura.",
                period.startEpoch + index * 20_000L, period.startEpoch + index * 21_000L, verified = index != 3,
            )
        }
        return ReportBuilder.build(period, samples, incidents, period.endEpoch + 3_600)
    }

    /** End to end against the live feeds, so it needs a connection. */
    @Test
    fun workerCollectsAndWritesTheReportOfTheDayThatClosed() = runBlocking {
        val container = (context as RadarTrenApp).container
        container.storage.clearSavedData()
        val yesterday = ReportPeriod.latestClosed(LocalDate.now(SPAIN_ZONE)).first()
        container.database.metrics().insert(MetricSampleEntity(yesterday.startEpoch + 3_600, 400, 380, 60, 10, 900, 45, 3))

        val result = TestListenableWorkerBuilder<CollectWorker>(context).build().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(container.database.reports().exists(yesterday.id))
        val pdf = File(container.storage.reportsDir, "radartren-${yesterday.id}.pdf")
        assertTrue(pdf.length() > 1_000)
        container.storage.clearSavedData()
    }

    @Test
    fun catalogAssetsLoad() {
        val app = context as RadarTrenApp
        assertNotNull(app.container.catalog.station("17000"))
        assertNull(app.container.catalog.station("nope"))
    }
}
