package com.example.facecollage.pipeline


import com.example.uniquepersnchlg.data.model.Identity
import com.example.uniquepersnchlg.data.model.Tracklet


class IdentityClusterer(
    private val similarityThreshold: Float = 0.62f
) {

    fun cluster(tracklets: List<Tracklet>): List<Identity> {
        if (tracklets.isEmpty()) return emptyList()

        // Each cluster starts as exactly one tracklet.
        data class Cluster(val members: MutableList<Tracklet>)
        val clusters = tracklets.map { Cluster(mutableListOf(it)) }.toMutableList()

        fun avgLinkageSim(c1: Cluster, c2: Cluster): Float {
            var total = 0f
            var count = 0
            for (t1 in c1.members) {
                val e1 = t1.centroidEmbedding()
                for (t2 in c2.members) {
                    total += cosineSim(e1, t2.centroidEmbedding())
                    count++
                }
            }
            return if (count == 0) -1f else total / count
        }

        while (true) {
            var bestI = -1
            var bestJ = -1
            var bestSim = similarityThreshold
            for (i in clusters.indices) {
                for (j in i + 1 until clusters.size) {
                    val sim = avgLinkageSim(clusters[i], clusters[j])
                    if (sim > bestSim) {
                        bestSim = sim
                        bestI = i
                        bestJ = j
                    }
                }
            }
            if (bestI == -1) break // no merge exceeds the threshold anymore
            clusters[bestI].members.addAll(clusters[bestJ].members)
            clusters.removeAt(bestJ)
        }

        return clusters.mapIndexed { idx, c ->
            Identity(id = idx, tracklets = c.members.sortedBy { it.startMs }.toMutableList())
        }.sortedByDescending { it.appearanceCount } // most-seen person first, purely cosmetic
    }
}
