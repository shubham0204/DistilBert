package com.example.distilbert.tokenization

class BasicTokenizer(
    private val doLowerCase: Boolean
) {

    companion object {

        @JvmStatic
        fun cleanText(
            text: String
        ): String {
            return text.filter {
                return@filter !CharChecker.isControl( it ) && !CharChecker.isInvalid( it )
            }
        }

        @JvmStatic
        fun whitespaceTokenize(
            text: String
        ): List<String> =
            text.split( " " )

        @JvmStatic
        fun runSplitOnPunc(
            text: String
        ): List<String> {
            val tokens = ArrayList<String>()
            var startNewWord = true
            text.forEach { ch ->
                if( CharChecker.isPunctuation( ch ) ) {
                    tokens.add( ch.toString() )
                    startNewWord = true
                }
                else {
                    if( startNewWord ) {
                        tokens.add( "" )
                        startNewWord = false
                    }
                    tokens[tokens.size - 1] = tokens.last() + ch
                }
            }
            return tokens
        }

    }

    fun tokenize(
        text: String
    ): List<String> {
        var origTokens: List<String> = whitespaceTokenize( cleanText( text ) )
        var cleanText = ""
        origTokens = origTokens.map{ token -> if( doLowerCase ) token.lowercase() else token }
        origTokens.forEach { token ->
            val list = runSplitOnPunc( token )
            for( subToken in list ) {
                cleanText += "$subToken "
            }
        }
        return whitespaceTokenize( cleanText )
    }

}