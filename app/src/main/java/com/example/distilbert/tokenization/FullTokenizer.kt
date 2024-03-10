package com.example.distilbert.tokenization

class FullTokenizer(
    private val inputDict: Map<String,Int> ,
    doLowerCase: Boolean
) {

    private val wordpieceTokenizer = WordpieceTokenizer( inputDict )
    private val basicTokenizer = BasicTokenizer( doLowerCase )


    fun tokenize(
        text: String
    ): List<String> {
        return basicTokenizer
            .tokenize( text )
            .map { wordpieceTokenizer.tokenize( it ) }
            .flatten()
    }

    fun convertTokensToIds(
        tokens: List<String>
    ): List<Int?> {
        return tokens
            .map { inputDict[ it ] }
    }

}