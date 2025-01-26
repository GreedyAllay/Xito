package io.github.ultreon.bot.data

import io.github.ultreon.bot.utils.fileExists
import io.github.ultreon.bot.utils.readFile
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture

abstract class CachedManager<Key, Value : Savable> {
  private val cache: MutableMap<Key, Value> = mutableMapOf()
  private val cleanUpScheduler: ScheduledExecutorService = createCleanUpScheduler()
  private val schedules: MutableMap<Key, ScheduledFuture<*>> = mutableMapOf()

  private fun createCleanUpScheduler(): ScheduledExecutorService {
    return Executors.newScheduledThreadPool(4) {
      Thread(it).apply {
        name = "CacheCleaner"
        isDaemon = true
      }
    }
  }

  open operator fun get(key: Key, secondary: Key? = null): Value {
    run LoadFromCache@{
      val remove = schedules.remove(key)
      val value = cache[key] ?: return@LoadFromCache
      val cancelled = remove?.cancel(false) ?: true

      if (!cancelled) {
        println("Failed to cancel $key, $secondary")
      }

      cleanUpScheduler.schedule({ cache.remove(key)?.also { it.save() } }, 10, java.util.concurrent.TimeUnit.MINUTES)
        .also {
          schedules[key] = it
        }
      return@get value
    }

    if (fileExists(path(key, secondary))) {
      val data = readFile(path(key, secondary))
      val value = load(key, secondary, data)
      cache[key] = value
      cleanUpScheduler.schedule({ cache.remove(key)?.also { it.save() } }, 10, java.util.concurrent.TimeUnit.MINUTES)
        .also {
          schedules[key] = it
        }
      return value
    }
    return create(key, secondary)
  }

  abstract fun path(key: Key, secondary: Key? = null): String

  abstract fun load(key: Key, secondary: Key? = null, data: String): Value

  abstract fun create(key: Key, secondary: Key? = null): Value
}
