package com.parapo.prototype

import android.app.Application
import android.content.Context
import com.parapo.prototype.data.RouteBundleStore
import com.parapo.prototype.data.RouteCatalog

class ParapoApplication : Application() {
    internal var catalogLoader: (Context) -> RouteCatalog? = { RouteBundleStore(it).load() }

}
