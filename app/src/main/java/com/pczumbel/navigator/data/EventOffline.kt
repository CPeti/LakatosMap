package com.pczumbel.navigator.data

import android.content.Context
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Makes an event's register entries and photos available with no signal — the survey maps in
 * particular, which are what you rig from.
 */
object EventOffline {

    data class Status(
        /** Cave points that have a cadastral number, i.e. can have a register entry at all. */
        val withRef: Int,
        val entriesSaved: Int,
        /** Photos listed by the saved entries, and how many of those are on the phone. */
        val photosKnown: Int,
        val photosSaved: Int,
        /** Cave points without a cadastral number, plus custom locations. */
        val noRef: Int,
    ) {
        val complete: Boolean get() = withRef > 0 && entriesSaved == withRef && photosSaved == photosKnown
    }

    data class Report(val status: Status, val failures: List<String>)

    private suspend fun refsFor(points: List<EventPoint>, lookup: suspend (Long) -> Cave?): Pair<List<Pair<String, String>>, Int> {
        val refs = mutableListOf<Pair<String, String>>() // ref to display name
        var noRef = 0
        for (p in points) {
            val ref = p.caveId?.let { lookup(it) }?.registerRef
            if (ref == null) noRef++ else refs += ref to p.name
        }
        return refs to noRef
    }

    private fun photoSaved(context: Context, url: String): Boolean =
        context.imageLoader.diskCache?.openSnapshot(url)?.use { true } ?: false

    /** What's on the phone right now; reads only local caches, no network. */
    suspend fun status(context: Context, points: List<EventPoint>, lookup: suspend (Long) -> Cave?): Status =
        withContext(Dispatchers.IO) {
            val (refs, noRef) = refsFor(points, lookup)
            val entries = refs.mapNotNull { Cadastre.cached(it.first) }
            val photos = entries.flatMap { it.images }.distinct()
            Status(
                withRef = refs.size,
                entriesSaved = entries.size,
                photosKnown = photos.size,
                photosSaved = photos.count { photoSaved(context, it) },
                noRef = noRef,
            )
        }

    /**
     * Fetch every register entry (refreshing the saved copy) and download every photo it lists.
     * Carries on past individual failures and reports them, so one dead link doesn't leave the
     * rest of the event unsaved.
     */
    suspend fun save(
        context: Context,
        points: List<EventPoint>,
        lookup: suspend (Long) -> Cave?,
        onProgress: (String) -> Unit,
    ): Report {
        val (refs, _) = refsFor(points, lookup)
        val failures = mutableListOf<String>()

        refs.forEachIndexed { i, (ref, name) ->
            onProgress("Register ${i + 1}/${refs.size}: $name")
            val entry = Cadastre.fetch(ref).getOrElse {
                failures += "$name: ${it.message}"
                Cadastre.cached(ref) // fall back to an older copy for its photo list
            } ?: return@forEachIndexed

            entry.images.forEachIndexed { j, url ->
                if (photoSaved(context, url)) return@forEachIndexed
                onProgress("Photos for $name ${j + 1}/${entry.images.size}")
                val result = context.imageLoader.execute(
                    ImageRequest.Builder(context)
                        .data(url)
                        // Only the disk copy matters; don't fill memory with decoded bitmaps.
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                )
                if (result !is SuccessResult) failures += "$name photo ${j + 1}"
            }
        }
        return Report(status(context, points, lookup), failures)
    }
}
