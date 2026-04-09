package com.noobdev.waffle.routing

import android.content.Context
import android.util.Log
import com.noobdev.waffle.utils.AssetCopier

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
