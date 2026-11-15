package ru.allisighs.funpaytools

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.map.Mapper
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade

object CoilSetup {
    private var done = false
    fun install() {
        if (done) return
        done = true
        SingletonImageLoader.setSafe { ctx: PlatformContext ->
            ImageLoader.Builder(ctx)
                .components {
                    add(KtorNetworkFetcherFactory())
                    add(Mapper<Uri, String> { data, _ -> data.toString() })
                    add(Mapper<File, String> { data, _ -> data.path })
                }
                .memoryCache { MemoryCache.Builder().maxSizePercent(ctx, 0.20).build() }
                .crossfade(false)
                .build()
        }
    }
}
