package work.lclpnet.ap2.game.pvp_tournament.gen

import work.lclpnet.ap2.ds.UnionFind

/**
 * Tracks byes in match subtrees.
 * After two subtrees are joined at a root node, both bye counts are added and taken as the new total bye count of the
 * joined subtree.
 */
class ByeTracker {

    private val classifier = UnionFind<Match>()
    private val byes = mutableMapOf<Match, Int>()

    @Synchronized
    fun register(match: Match) {
        classifier.add(match)
    }

    @Synchronized
    fun byeCountInTree(match: Match): Int {
        val classOf = classifier.find(match) ?: return 0
        return byes[classOf] ?: 0
    }

    @Synchronized
    fun mergeTrees(a: Match, b: Match) {
        val classA = classifier.find(a) ?: return
        val classB = classifier.find(b) ?: return

        if (classA == classB) return

        val byesInA = byes.remove(classA) ?: 0
        val byesInB = byes.remove(classB) ?: 0

        classifier.union(a, b)

        val newClass = classifier.find(a) ?: return

        byes[newClass] = byesInA + byesInB
    }

    @Synchronized
    fun addBye(match: Match) {
        val classOfByes = classifier.find(match) ?: return
        byes.merge(classOfByes, 1, Int::plus)
    }

    @Synchronized
    fun chooseBye(matches: List<Match>): Match? {
        val matchesWithByeCount = matches.mapNotNull {
            val classOfIt = classifier.find(it) ?: return@mapNotNull null
            it to (byes[classOfIt] ?: 0)
        }

        if (matchesWithByeCount.isEmpty()) return null

        val minByeCount = matchesWithByeCount.minOf { (_, byeCount) -> byeCount }

        return matchesWithByeCount
            .filter { (_, byeCount) -> byeCount == minByeCount }
            .map { (match, _) -> match }
            .randomOrNull()
    }
}