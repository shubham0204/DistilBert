package com.example.distilbert.tokenization

class WordpieceTokenizer(private val dict: Map<String, Int>) {

    companion object {
        private val unknownToken = "[UNK]"
        private val maxInputWordPerChar = 200
    }

    fun tokenize(text: String): List<String> {
        val outputTokens = ArrayList<String>()
        BasicTokenizer.whitespaceTokenize(text).forEach { token ->
            if (token.length > maxInputWordPerChar) {
                outputTokens.add(token)
                return@forEach
            }
            var isBad = false
            var start = 0
            val subTokens = ArrayList<String>()
            while (start < token.length) {
                var curSubStr = ""
                var end = token.length
                while (start < end) {
                    val subStr =
                        if (start == 0) token.substring(start, end)
                        else "##" + token.substring(start, end)
                    if (dict.containsKey(subStr)) {
                        curSubStr = subStr
                        break
                    }
                    end--
                }
                if ("" == curSubStr) {
                    isBad = true
                    break
                }
                subTokens.add(curSubStr)
                start = end
            }

            if (isBad) {
                outputTokens.add(WordpieceTokenizer.unknownToken)
            } else {
                outputTokens.addAll(subTokens)
            }
        }

        return outputTokens
    }
}
