package com.tuusuario.m3u

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class M3uPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(M3uProvider())
    }
}
