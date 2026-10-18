package com.click.lightmemo.network

/** Recalls every possible positive-score match, preserving source order for score ties. */
internal class FoodSearchIndex<T>(private val items: List<T>, terms: (T) -> Set<String>) {
    private val postings: Map<String, IntArray> = buildMap<String, MutableList<Int>> {
        items.forEachIndexed { index, item ->
            terms(item).forEach { term -> getOrPut(term) { mutableListOf() }.add(index) }
        }
    }.mapValues { (_, indices) -> indices.toIntArray() }

    fun candidates(terms: Set<String>): List<T> = terms.asSequence()
        .flatMap { postings[it]?.asSequence() ?: emptySequence() }
        .distinct()
        .sorted()
        .map { items[it] }
        .toList()
}
