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
package com.example.distilbert

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.speech.tts.TextToSpeech
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.util.Log
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.distilbert.ml.LoadDatasetClient
import com.example.distilbert.ml.QaAnswer
import com.example.distilbert.ml.QaClient
import com.example.distilbert.ui.theme.DistilBertTheme
import java.util.Locale

/** Activity for doing Q&A on a specific dataset  */
class QaActivity : AppCompatActivity() {

    private var textToSpeech: TextToSpeech? = null
    private var questionAnswered = false
    private var handler: Handler? = null
    private var qaClient: QaClient? = null

    private val titleState: MutableState<String> = mutableStateOf( "" )
    private val contentState: MutableState<String> = mutableStateOf( "" )
    private val questionState: MutableState<String> = mutableStateOf( "" )
    private val suggestionsState: MutableState<List<String>> = mutableStateOf( listOf() )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ActivityUI()
        }

        // Get content of the selected dataset.
        val datasetPosition = intent.getIntExtra(DATASET_POSITION_KEY, -1)
        val datasetClient = LoadDatasetClient(this)

        // Show the dataset title.
        titleState.value = datasetClient.titles[datasetPosition]

        // Show the text content of the selected dataset.
        contentState.value = datasetClient.getContent(datasetPosition)

        // Setup question suggestion list.
        suggestionsState.value = datasetClient.getQuestions(datasetPosition).toList()
        /*
        val questionSuggestionsView = findViewById<RecyclerView>(R.id.suggestion_list)
        val adapter = QuestionAdapter(this, datasetClient.getQuestions(datasetPosition))
        adapter.setOnQuestionSelectListener { question: String -> answerQuestion(question) }
        questionSuggestionsView.setAdapter(adapter)
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        questionSuggestionsView.setLayoutManager(layoutManager)

         */

        // Setup QA client to and background thread to run inference.
        val handlerThread = HandlerThread("QAClient")
        handlerThread.start()
        handler = Handler(handlerThread.getLooper())
        qaClient = QaClient(this)
    }

    @Composable
    private fun ActivityUI() {
        DistilBertTheme {
            Surface( modifier = Modifier
                .fillMaxSize()
                .background(Color.White) ) {
                Column {
                    PassageDisplay()
                    QuestionSuggestions()
                    QuestionInput()
                }
            }
        }
    }

    @Composable
    private fun QuestionInput() {
        var question by remember{ questionState }
        var askButtonEnabled by remember{ mutableStateOf( false ) }
        Row {
            TextField(
                value = question,
                onValueChange = {
                    question = it
                    askButtonEnabled = question.trim().isNotEmpty()
                }
            )
            Button(
                onClick = { answerQuestion( question ) } ,
                enabled = askButtonEnabled
            ) {
                Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "Ask Question")
            }
        }
    }

    @Composable
    private fun QuestionSuggestions() {
        val suggestions by remember{ suggestionsState }
        Column {
            Text(text = "You might want to ask ..." )
            Row {
                suggestions.forEach {
                    Text(text = it)
                }
            }
        }
    }

    @Composable
    private fun PassageDisplay() {
        val title by remember{ titleState }
        val content by remember{ contentState }
        Column {
            Text(text = title )
            Text(text = content)
        }
    }

    override fun onStart() {
        Log.v(TAG, "onStart")
        super.onStart()
        handler!!.post {
            qaClient!!.loadModel()
            qaClient!!.loadDictionary()
        }
        textToSpeech = TextToSpeech(
            this
        ) { status: Int ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech!!.setLanguage(Locale.US)
            } else {
                textToSpeech = null
            }
        }
    }

    override fun onStop() {
        Log.v(TAG, "onStop")
        super.onStop()
        handler!!.post { qaClient!!.unload() }
        if (textToSpeech != null) {
            textToSpeech!!.stop()
            textToSpeech!!.shutdown()
        }
    }

    private fun answerQuestion(question: String) {
        var question = question
        question = question.trim { it <= ' ' }
        if (question.isEmpty()) {
            questionState.value = question
            return
        }

        // Append question mark '?' if not ended with '?'.
        // This aligns with question format that trains the model.
        if (!question.endsWith("?")) {
            question += '?'
        }
        questionState.value = question

        // Delete all pending tasks.
        handler!!.removeCallbacksAndMessages(null)

        // Hide keyboard and dismiss focus on text edit.
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
        val focusView = currentFocus
        focusView?.clearFocus()

        // Reset content text view
        questionAnswered = false

        // Run TF Lite model to get the answer.
        handler!!.post {
            val beforeTime = System.currentTimeMillis()
            val answers = qaClient!!.predict( question , contentState.value)
            val afterTime = System.currentTimeMillis()
            val totalSeconds = (afterTime - beforeTime) / 1000.0
            if (answers.isNotEmpty()) {
                // Get the top answer
                val topAnswer = answers[0]
                // Show the answer.
                runOnUiThread {
                    presentAnswer(topAnswer)
                    var displayMessage = "Top answer was successfully highlighted."
                    if (DISPLAY_RUNNING_TIME) {
                        displayMessage = String.format("%s %.3fs.", displayMessage, totalSeconds)
                    }
                    // Snackbar.make(contentTextView!!, displayMessage, Snackbar.LENGTH_LONG).show()
                    questionAnswered = true
                }
            }
        }
    }

    private fun presentAnswer(answer: QaAnswer) {
        // Highlight answer.
        val spanText: Spannable = SpannableString(contentState.value)
        val offset = contentState.value.indexOf(answer.text, 0)
        if (offset >= 0) {
            spanText.setSpan(
                BackgroundColorSpan(getColor(R.color.secondaryColor)),
                offset,
                offset + answer.text.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        contentState.value = spanText.toString()

        // Use TTS to speak out the answer.
        if (textToSpeech != null) {
            textToSpeech!!.speak(answer.text, TextToSpeech.QUEUE_FLUSH, null, answer.text)
        }
    }

    companion object {
        private const val DATASET_POSITION_KEY = "DATASET_POSITION"
        private const val TAG = "QaActivity"
        private const val DISPLAY_RUNNING_TIME = false
        fun newInstance(context: Context?, datasetPosition: Int): Intent {
            val intent = Intent(context, QaActivity::class.java)
            intent.putExtra(DATASET_POSITION_KEY, datasetPosition)
            return intent
        }
    }
}
