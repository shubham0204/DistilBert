package com.example.distilbert.ml

data class Feature(
    val inputIds: IntArray,
    val inputMask: IntArray,
    val segmentIds: IntArray,
    val origTokens: List<String>,
    val tokenToOrigMap: Map<Int, Int>
) {

    constructor(
        inputIds: List<Int>,
        inputMask: List<Int>,
        segmentIds: List<Int>,
        origTokens: List<String>,
        tokenToOrigMap: Map<Int, Int>
    ) : this(
        inputIds.toIntArray(),
        inputMask.toIntArray(),
        segmentIds.toIntArray(),
        origTokens,
        tokenToOrigMap
    )
}
