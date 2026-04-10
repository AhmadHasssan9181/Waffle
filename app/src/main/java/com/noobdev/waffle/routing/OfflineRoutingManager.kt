package com.noobdev.waffle.routing

import android.content.Context
import android.util.Log
import com.noobdev.waffle.utils.AssetCopier

//this is like the loader of the engine inside the coroutine which is defined in the mainactivity
//go to main activity youd find there a thing called Dispatcher.io this is like to not hold the ui
//so that it wont stuck and it goes smoothly

//main feature as well  = It takes a start Lat/Lng and an end Lat/Lng and returns
// a GHResponse containing the exact route coordinates of the places on map

object OfflineRoutingManager {
    private const val TAG = "OfflineRoutingManager"

    @Volatile private var engine: LocalRoutingEngine? = null

    fun initialize(context: Context): LocalRoutingEngine {
        engine?.let { existing ->
            Log.d(TAG, "Reusing existing LocalRoutingEngine instance")
            return existing
        }

        return synchronized(this) {
            engine?.let { existing ->
                Log.d(TAG, "Reusing existing LocalRoutingEngine instance after lock")
                existing
            }
                    ?: run {
                        val graphDir = AssetCopier.copyGraphCacheToStorage(context)
                        Log.d(
                                TAG,
                                "Initializing GraphHopper from ${graphDir.absolutePath}"
                        )

                        LocalRoutingEngine(graphDir.absolutePath).also { initialized ->
                            engine = initialized
                            Log.d(TAG, "GraphHopper initialization finished successfully")
                        }
                    }
        }
    }

    fun getEngineOrNull(): LocalRoutingEngine? = engine
}
