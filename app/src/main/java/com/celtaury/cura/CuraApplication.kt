package com.celtaury.cura

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.celtaury.cura.core.CuraStrings

/**
 * One shared Coil image loader for the whole app.
 * - VideoFrameDecoder: video rows get real thumbnails (Large Files, Social Cleaner)
 * - crossfade: soft fade-in instead of a hard pop, everywhere
 */
class CuraApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        CuraStrings.init(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(150)
            .build()
}
