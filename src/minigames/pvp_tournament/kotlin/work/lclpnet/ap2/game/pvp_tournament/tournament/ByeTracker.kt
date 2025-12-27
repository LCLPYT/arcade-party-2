package work.lclpnet.ap2.game.pvp_tournament.tournament

import work.lclpnet.ap2.ds.UnionFind

/**
 * Tracks byes in match subtrees.
 * After two subtrees are joined at a root node, both bye counts are added and taken as the new total bye count of the
 * joined subtree.
 */
class ByeTracker {

    private val classifier = UnionFind<Match>()
    private val byes = mutableMapOf<Match, Int>()

    fun register(match: Match) {
        classifier.add(match)
    }

    fun byeCountInTree(match: Match): Int {
        val classOf = classifier.find(match) ?: return 0

        synchronized(this) {
            return byes[classOf] ?: 0
        }
    }

    fun mergeTrees(a: Match, b: Match) {
        val classA = classifier.find(a) ?: return
        val classB = classifier.find(b) ?: return

        if (classA == classB) return

        synchronized(this) {
            val byesInA = byes.remove(classA) ?: 0
            val byesInB = byes.remove(classB) ?: 0

            classifier.union(a, b)

            val newClass = classifier.find(a) ?: return

            byes[newClass] = byesInA + byesInB
        }
    }

    fun addBye(match: Match) {
        val classOfByes = classifier.find(match) ?: return

        synchronized(this) {
            byes.compute(classOfByes) { _, prev -> if (prev == null) 1 else prev + 1 }
        }
    }

    @Synchronized
    fun chooseBye(matches: List<Match>): Match? {
        val matchesWithByeCount = matches.mapNotNull {
            val classOfIt = classifier.find(it)

            if (classOfIt != null) {
                it to (byes[classOfIt] ?: 0)
            } else {
                null
            }
        }

        val minByeCount = matchesWithByeCount.minOf { (_, byeCount) -> byeCount }

        val treesWithLeastByes = matchesWithByeCount
            .filter { (_, byeCount) -> byeCount == minByeCount }
            .map { (match, _) -> match }

        return treesWithLeastByes.randomOrNull()
    }
}