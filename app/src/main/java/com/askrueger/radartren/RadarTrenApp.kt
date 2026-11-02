package com.askrueger.radartren

import android.app.Application
import com.askrueger.radartren.data.IncidentRepository
import com.askrueger.radartren.data.LiveData
import com.askrueger.radartren.data.LiveRepository
import com.askrueger.radartren.data.OfflineMaps
import com.askrueger.radartren.data.SettingsRepository
import com.askrueger.radartren.data.Storage
import com.askrueger.radartren.data.local.IncidentEntity
import com.askrueger.radartren.data.local.RadarDatabase
import com.askrueger.radartren.data.remote.RenfeClient
import com.askrueger.radartren.domain.Severity
import com.askrueger.radartren.domain.StaticCatalog
import com.askrueger.radartren.report.ReportGenerator
import com.askrueger.radartren.ui.common.localized
import com.askrueger.radartren.work.CollectWorker
import com.askrueger.radartren.work.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil
import java.util.concurrent.TimeUnit

class RadarTrenApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.initMaps()
        Notifier.createChannels(this)
        container.scope.launch {
            container.settings.settings
                .distinctUntilChangedBy { it.backgroundCollection to it.wifiOnly }
                .collect { CollectWorker.schedule(this@RadarTrenApp, it) }
        }
    }
}

/** Hand-wired dependencies, created once for the whole process. */
class AppContainer(private val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val userAgent = "RadarTren/${BuildConfig.VERSION_NAME} (Android)"

    private val http = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
        }
        .build()

    val catalog: StaticCatalog by lazy {
        fun asset(name: String) = app.assets.open("catalog/$name").bufferedReader().use { it.readLines() }.asSequence()
        StaticCatalog.parse(asset("stations.csv"), asset("lines.csv"), asset("trains.csv"))
    }

    val settings = SettingsRepository(app)
    val database = RadarDatabase.create(app)
    val live by lazy { LiveRepository(RenfeClient(http), catalog) }
    val incidents by lazy { IncidentRepository(database, catalog) }
    val offlineMaps = OfflineMaps(app)
    val storage by lazy { Storage(app, database, offlineMaps) }
    val reports by lazy { ReportGenerator(database, storage) }

    /**
     * Must run on the main thread, before any map or background work touches MapLibre. OpenFreeMap
     * asks offline downloads for a unique User-Agent and at most 4 parallel requests.
     */
    fun initMaps() {
        MapLibre.getInstance(app)
        HttpRequestUtil.setOkHttpClient(
            http.newBuilder().dispatcher(Dispatcher().apply { maxRequestsPerHost = 4 }).build(),
        )
    }

    /** True while the app is on screen, where new incidents appear inside it instead. */
    @Volatile
    var foreground = false

    private val _announcements = MutableSharedFlow<List<IncidentEntity>>(extraBufferCapacity = 8)
    val announcements: SharedFlow<List<IncidentEntity>> = _announcements

    /** One full cycle: read the feeds, update the incidents and announce what is new. */
    suspend fun collect(): LiveData {
        val settings = settings.current()
        val data = live.refresh()
        val announce = incidents.record(data, System.currentTimeMillis() / 1000)
        if (foreground) {
            announce.filter { it.severity >= Severity.MEDIUM }.takeIf { it.isNotEmpty() }?.let(_announcements::tryEmit)
        } else {
            settings.notifyFrom?.let { threshold ->
                Notifier.notify(localized(app, settings.language), announce.filter { it.severity >= threshold })
            }
        }
        return data
    }
}
