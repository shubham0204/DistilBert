package com.example.distilbert.ml

class QaAnswer(
    val text: String ,
    val pos: Pos
) {

    class Pos(
        val start: Int ,
        val end: Int ,
        val logit: Float
    ): Comparable<Pos> {

        override fun compareTo(other: Pos): Int {
            return other.logit.compareTo( logit )
        }

    }

}