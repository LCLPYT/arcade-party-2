package work.lclpnet.ap2.ds

/**
 * Equivalence-class data structure.
 * Maps each element to an equivalence class.
 * Supports operations for adding, merging or getting equivalence classes of elements.
 */
class UnionFind<K> {

    private val classes = mutableMapOf<K, K>()
    private val byClass = mutableMapOf<K, MutableSet<K>>()

    /**
     * Adds an element to the data structure.
     * The element is added to a new equivalence class with just itself as single element.
     * If the element already exists in this data structure, nothing is done.
     */
    @Synchronized
    fun add(elem: K) {
        if (classes.containsKey(elem)) return

        classes[elem] = elem
        byClass.computeIfAbsent(elem) { mutableSetOf() }.add(elem)
    }

    /**
     * Returns the equivalence class of an element.
     * If the element was not added to this data structure, null is returned.
     */
    @Synchronized
    fun find(elem: K): K? {
        return classes[elem]
    }

    /**
     * Returns all members of the equivalence class of the given element.
     */
    @Synchronized
    fun findAll(elem: K): Set<K> {
        val classOfElem = find(elem) ?: return emptySet()

        return byClass[classOfElem] ?: emptySet()
    }

    @Synchronized
    fun classes(): Set<K> {
        return byClass.keys.toSet()
    }

    /**
     * Merges the equivalence classes specified by two class members.
     * If the elements are in the same class already, nothing is done.
     * If either element does not have a class in this data structure, nothing is done.
     * After this call, all previous members of the class of elem will be in the class of the other.
     */
    @Synchronized
    fun union(elem: K, other: K) {
        if (elem == other) return

        val classOfElem = find(elem) ?: return
        val classOfOther = find(other) ?: return

        if (classOfElem == classOfOther) return

        // move all members of class of elem to class of classMember
        val elemClassMembers = byClass.remove(classOfElem) ?: return

        elemClassMembers.forEach { classes[it] = classOfOther }

        byClass.computeIfAbsent(classOfOther) { mutableSetOf() }.addAll(elemClassMembers)
    }
}