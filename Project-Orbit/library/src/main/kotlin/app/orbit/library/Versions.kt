package app.orbit.library

object Versions {
    fun parse(v: String): List<Int> = v.trim().split('.', '-', '_').mapNotNull { it.toIntOrNull() }

    /** Compares numeric parts; missing parts count as 0. */
    fun compare(a: String, b: String): Int {
        val x = parse(a); val y = parse(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    /** True if [installed] shares major.minor.patch with [target] (build number ignored). */
    fun sameRelease(installed: String, target: String): Boolean {
        val a = parse(installed).take(3); val b = parse(target).take(3)
        return a.isNotEmpty() && a == b
    }
}
