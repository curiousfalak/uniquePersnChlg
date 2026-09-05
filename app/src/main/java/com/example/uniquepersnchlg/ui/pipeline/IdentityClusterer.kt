package com.example.uniquepersnchlg.pipeline

import com.example.facecollage.pipeline.cosineSim
import com.example.uniquepersnchlg.data.Identity
import com.example.uniquepersnchlg.data.Tracklet

/**
 * Stage B of the pipeline: groups tracklets (appearances) from across the whole video into
 * per-person identities.
 *
 * HISTORY / DESIGN NOTE - this went through two prior versions, each fixing one failure mode and
 * introducing another, worth recording so the next change doesn't repeat either mistake:
 *
 * v1 (centroid-average-linkage): average ALL of a tracklet's frames into one centroid embedding,
 * compare centroids. Fragile to head-pose variance - if one appearance is mostly frontal and
 * another of the same person is mostly turned, averaging dilutes the strong frontal-to-frontal
 * evidence and the two centroids end up too dissimilar to merge, splitting one person into two
 * identities. Confirmed on real footage.
 *
 * v2 (top-2-of-best-frames "max linkage"): compare each tracklet's top-K individual frame
 * embeddings directly (never blended into one vector), score a pair by the average of just the
 * top-2 highest cross-frame similarities. Fixed the pose-dilution problem, but agglomerative
 * clustering CHAINS on a max-linkage-like criterion: a merged cluster has more frames, hence more
 * chances for just 2 coincidentally-high similarities against some other cluster, which merges
 * them too, growing the pool further - one lucky pair can cascade into merging everyone into a
 * single identity. Confirmed on real footage (collage collapsed to 1 person).
 *
 * v3 (current) - full average-linkage over the SAME per-frame comparison set: still compare
 * individual best frames directly (keeps v2's pose-robustness - never collapse either side into
 * one diluted vector), but score a pair by the MEAN of every cross-frame similarity in the top-K
 * x top-K grid, not just the top 2. This requires the whole set of comparisons to support a
 * match on average, which is far more resistant to chaining/over-merging than cherry-picking a
 * couple of high values, while still avoiding v1's specific failure since no embeddings are ever
 * averaged together before comparing - only the resulting SIMILARITY SCORES are averaged.
 *
 * Agglomerative (rather than a single greedy left-to-right pass) is used because it isn't
 * order-dependent: with a greedy pass, an early borderline merge can permanently poison a
 * cluster and cause a cascade of wrong merges later. With only a handful of tracklets per 30s
 * clip, the extra comparisons this method costs are still essentially free.
 *
 * [similarityThreshold] needs re-validation against THIS metric (it is not on the same scale as
 * either prior version). Log output from VideoProcessor reports this exact metric per tracklet
 * pair after every run - use that to set the threshold from real numbers, not guesswork.
 */
class IdentityClusterer(
    private val similarityThreshold: Float = 0.45f,
    private val topKFramesPerTracklet: Int = 6
) {

    fun cluster(tracklets: List<Tracklet>): List<Identity> {
        if (tracklets.isEmpty()) return emptyList()

        // Each cluster starts as exactly one tracklet.
        data class Cluster(val members: MutableList<Tracklet>)
        val clusters = tracklets.map { Cluster(mutableListOf(it)) }.toMutableList()

        fun bestFrameEmbeddings(t: Tracklet): List<FloatArray> =
            t.samples.sortedByDescending { it.qualityScore() }.take(topKFramesPerTracklet).map { it.embedding }

        fun clusterScore(c1: Cluster, c2: Cluster): Float {
            val embeddingsA = c1.members.flatMap { bestFrameEmbeddings(it) }
            val embeddingsB = c2.members.flatMap { bestFrameEmbeddings(it) }
            if (embeddingsA.isEmpty() || embeddingsB.isEmpty()) return -1f

            var total = 0f
            var count = 0
            for (a in embeddingsA) for (b in embeddingsB) { total += cosineSim(a, b); count++ }
            return if (count == 0) -1f else total / count
        }

        while (true) {
            var bestI = -1
            var bestJ = -1
            var bestSim = similarityThreshold
            for (i in clusters.indices) {
                for (j in i + 1 until clusters.size) {
                    val sim = clusterScore(clusters[i], clusters[j])
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

    companion object {
        /**
         * Same full average-linkage-over-frame-pairs metric used internally, exposed so
         * VideoProcessor's diagnostic logging reports the EXACT number clustering acts on.
         */
        fun bestPairScore(t1: Tracklet, t2: Tracklet, topK: Int = 6): Float {
            val embeddingsA = t1.samples.sortedByDescending { it.qualityScore() }.take(topK).map { it.embedding }
            val embeddingsB = t2.samples.sortedByDescending { it.qualityScore() }.take(topK).map { it.embedding }
            if (embeddingsA.isEmpty() || embeddingsB.isEmpty()) return -1f
            var total = 0f
            var count = 0
            for (a in embeddingsA) for (b in embeddingsB) { total += cosineSim(a, b); count++ }
            return if (count == 0) -1f else total / count
        }
    }
}
