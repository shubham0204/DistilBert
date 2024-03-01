package com.example.distilbert.tokenization

class CharChecker {

    companion object {

        @JvmStatic
        fun isInvalid(
            character: Char
        ): Boolean {
            return character.code == 0 || character.code == 0xfffd
        }

        @JvmStatic
        fun isControl(
            character: Char
        ): Boolean {
            if( Character.isWhitespace( character ) ) {
                return false
            }
            Character.getType( character ).toByte().also {
                return it == Character.CONTROL || it == Character.FORMAT
            }
        }

        @JvmStatic
        fun isWhitespace(
            character: Char
        ): Boolean {
            if( Character.isWhitespace( character ) ) {
                return true
            }
            Character.getType( character ).toByte().also {
                return it == Character.SPACE_SEPARATOR ||
                        it == Character.LINE_SEPARATOR ||
                        it == Character.PARAGRAPH_SEPARATOR
            }
        }

        @JvmStatic
        fun isPunctuation(
            character: Char
        ): Boolean {
            Character.getType( character ).toByte().also {
                return it == Character.CONNECTOR_PUNCTUATION ||
                        it == Character.DASH_PUNCTUATION ||
                        it == Character.START_PUNCTUATION ||
                        it == Character.END_PUNCTUATION ||
                        it == Character.INITIAL_QUOTE_PUNCTUATION ||
                        it == Character.FINAL_QUOTE_PUNCTUATION ||
                        it == Character.OTHER_PUNCTUATION
            }
        }

    }

}