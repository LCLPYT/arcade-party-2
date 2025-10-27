package work.lclpnet.ap2

import it.unimi.dsi.fastutil.objects.ObjectIntPair

operator fun <K> ObjectIntPair<K>.component1(): K? = first()
operator fun <K> ObjectIntPair<K>.component2() = secondInt()