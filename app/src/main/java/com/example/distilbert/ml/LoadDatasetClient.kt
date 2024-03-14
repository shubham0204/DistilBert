/* Copyright 2019 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/
package com.example.distilbert.ml

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * Interface to load squad dataset. Provide passages for users to choose from & provide questions
 * for autoCompleteTextView.
 */
class LoadDatasetClient(private val context: Context) {

    lateinit var titles: Array<String>
    private lateinit var contents: Array<String>
    private lateinit var questions: Array<Array<String>>

    init {
        loadJson()
    }

    companion object {

        private const val TAG = "BertAppDemo"
        private const val JSON_DIR = "qa.json"
        private const val DIC_DIR = "vocab.txt"

    }

    private fun loadJson() {
        try {
            val reader = JsonReader(
                InputStreamReader(
                    context.assets.open(JSON_DIR)
                )
            )
            val map =
                Gson().fromJson<HashMap<String, List<List<String>>>>(reader, HashMap::class.java)
            val jsonTitles = map["titles"] ?: emptyList()
            val jsonContents = map["contents"] ?: emptyList()
            val jsonQuestions = map["questions"] ?: emptyList()
            titles = jsonTitles.map { it[0] }.toTypedArray()
            contents = jsonContents.map { it[0] }.toTypedArray()
            val questionsList = ArrayList<Array<String>>()
            jsonQuestions.forEach { item ->
                questionsList.add(item.toTypedArray<String>())
            }
            questions = questionsList.toTypedArray()
        } catch (ex: IOException) {
            Log.e(TAG, ex.toString())
        }
    }

    fun getContent(index: Int): String {
        return contents[index]
    }

    fun getQuestions(index: Int): Array<String> {
        return questions[index]
    }

    fun loadDictionary(): Map<String, Int> {
        val dic: MutableMap<String, Int> = HashMap()
        try {
            context.assets.open(DIC_DIR).use { ins ->
                BufferedReader(InputStreamReader(ins)).use { reader ->
                    var index = 0
                    while (reader.ready()) {
                        val key = reader.readLine()
                        dic[key] = index++
                    }
                }
            }
        } catch (ex: IOException) {
            Log.e(TAG, ex.message!!)
        }
        return dic
    }

}
