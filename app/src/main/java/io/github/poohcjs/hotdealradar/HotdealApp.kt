package io.github.poohcjs.hotdealradar

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import io.github.poohcjs.hotdealradar.data.Prefs
import io.github.poohcjs.hotdealradar.data.Repository
import io.github.poohcjs.hotdealradar.work.DealCheckWorker

class HotdealApp : Application(), SingletonImageLoader.Factory {
    lateinit var prefs: Prefs
        private set
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        repository = Repository(this, prefs)
        DealCheckWorker.createChannel(this)
        val s = prefs.settings.value
        DealCheckWorker.schedule(this, s.notify || s.notifyTarget)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context).crossfade(true).build()
}
