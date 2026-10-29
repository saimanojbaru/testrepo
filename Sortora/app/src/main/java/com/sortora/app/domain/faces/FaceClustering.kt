package com.sortora.app.domain.faces

import com.sortora.app.domain.classify.ImageMetrics

/**
 * Groups face embeddings into "same-looking person" clusters.
 *
 * Steps (all done once in [FaceClustering.index]; changing the strictness afterwards is instant):
 *  1. Centre the embeddings on their mean and re-normalise. Every face crop looks alike to a
 *     general-purpose model; removing the shared "face" component makes the differences count.
 *  2. Find each face's [NEIGHBOURS] most similar faces.
 *  3. Link two faces only if each is among the other's nearest neighbours and their similarity
 *     clears the threshold. Mutual neighbours stop one ambiguous face from chaining two different
 *     people into one cluster (the classic single-linkage failure).
 *
 * This is visual similarity, not identity recognition: look-alikes can merge and very different
 * photos of one person can split. The UI lets people tune strictness and name clusters.
 */
object FaceClustering {

    const val NEIGHBOURS = 8
    const val DEFAULT_THRESHOLD = 0.45f
    /** Above this many faces the one-off similarity pass gets slow on phones; the rest are skipped. */
    const val MAX_FACES = 1500

    class Index internal constructor(
        val size: Int,
        /** Candidate links as (similarity, a, b), strongest first. */
        private val edges: List<Edge>,
    ) {
        internal data class Edge(val similarity: Float, val a: Int, val b: Int)

        /** Clusters (lists of input indices), biggest first; singletons are included. */
        fun clusters(threshold: Float): List<List<Int>> {
            val parent = IntArray(size) { it }
            fun find(i: Int): Int {
                var x = i
                while (parent[x] != x) {
                    parent[x] = parent[parent[x]]
                    x = parent[x]
                }
                return x
            }
            for (edge in edges) {
                if (edge.similarity < threshold) break
                val ra = find(edge.a)
                val rb = find(edge.b)
                if (ra != rb) parent[maxOf(ra, rb)] = minOf(ra, rb)
            }
            return (0 until size).groupBy(::find).values
                .sortedWith(compareByDescending<List<Int>> { it.size }.thenBy { it.first() })
        }
    }

    fun index(embeddings: List<FloatArray>): Index {
        val faces = embeddings.take(MAX_FACES)
        val n = faces.size
        if (n == 0) return Index(0, emptyList())
        val dim = faces.first().size

        // 1. Centre + normalise.
        val mean = FloatArray(dim)
        faces.forEach { v -> for (d in 0 until dim) mean[d] += v[d] / n }
        val centred = faces.map { v -> ImageMetrics.normalize(FloatArray(dim) { d -> v[d] - mean[d] }) }

        // 2. Pairwise similarities and each face's nearest neighbours.
        val k = NEIGHBOURS.coerceAtMost(n - 1)
        val neighbours = Array(n) { HashSet<Int>() }
        val sims = Array(n) { FloatArray(n) }
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val s = ImageMetrics.dot(centred[i], centred[j])
                sims[i][j] = s
                sims[j][i] = s
            }
        }
        if (k > 0) {
            for (i in 0 until n) {
                (0 until n).filter { it != i }.sortedByDescending { sims[i][it] }.take(k).forEach { neighbours[i] += it }
            }
        }

        // 3. Mutual-neighbour edges, strongest first.
        val edges = ArrayList<Index.Edge>()
        for (i in 0 until n) {
            for (j in neighbours[i]) {
                if (j > i && i in neighbours[j]) edges += Index.Edge(sims[i][j], i, j)
            }
        }
        edges.sortByDescending { it.similarity }
        return Index(n, edges)
    }

    /** The most common name already given to faces in a cluster, if any. */
    fun clusterName(names: List<String?>): String? =
        names.filterNotNull().filter { it.isNotBlank() }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
}
