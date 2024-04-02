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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.distilbert.ml.LoadDatasetClient
import com.example.distilbert.ml.QaAnswer
import com.example.distilbert.ml.QaClient
import com.example.distilbert.ui.theme.DistilBertTheme
import java.util.Locale

/** Activity for doing Q&A on a specific dataset */
class QaActivity : AppCompatActivity() {

    private var textToSpeech: TextToSpeech? = null
    private var questionAnswered = false
    private lateinit var handler: Handler
    private lateinit var qaClient: QaClient

    private val titleState: MutableState<String> = mutableStateOf("")
    private val contentState: MutableState<String> = mutableStateOf("")
    private val questionState: MutableState<String> = mutableStateOf("")
    private val suggestionsState: MutableState<List<String>> = mutableStateOf(listOf())
    private val showSuggestionsDialogState: MutableState<Boolean> = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ActivityUI() }

        // Get content of the selected dataset.
        val datasetPosition = intent.getIntExtra(DATASET_POSITION_KEY, -1)
        val datasetClient = LoadDatasetClient(this)

        // Show the dataset title.
        titleState.value = datasetClient.titles[datasetPosition]

        // Show the text content of the selected dataset.
        contentState.value = datasetClient.getContent(datasetPosition)

        // Setup question suggestion list.
        suggestionsState.value = datasetClient.getQuestions(datasetPosition).toList()

        // Setup QA client to and background thread to run inference.
        val handlerThread = HandlerThread("QAClient")
        handlerThread.start()
        handler = Handler(handlerThread.getLooper())
        qaClient = QaClient(this)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ActivityUI() {
        DistilBertTheme {
            Surface(modifier = Modifier.fillMaxSize().background(Color.White)) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            colors =
                                TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    titleContentColor = MaterialTheme.colorScheme.primary,
                                ),
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        imageVector = Icons.Filled.ArrowBack,
                                        contentDescription = "Navigate Back"
                                    )
                                }
                            },
                            title = {
                                val title by remember { titleState }
                                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        )
                    }
                ) { paddingValues ->
                    Column(modifier = Modifier.padding(paddingValues)) {
                        PassageDisplay()
                        QuestionSuggestionsDialog()
                        QuestionInput()
                    }
                }
            }
        }
    }

    @Composable
    private fun QuestionInput() {
        var question by remember { questionState }
        var askButtonEnabled by remember { mutableStateOf(false) }
        Row(modifier = Modifier.padding(8.dp)) {
            TextField(
                value = question,
                onValueChange = {
                    question = it
                    askButtonEnabled = question.trim().isNotEmpty()
                },
                trailingIcon = {
                    IconButton(onClick = { showSuggestionsDialogState.value = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Show question suggestions"
                        )
                    }
                },
                placeholder = { Text(text = "Type your question here...") },
                colors =
                    TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(4.dp))
            Button(onClick = { answerQuestion(question) }, enabled = askButtonEnabled) {
                Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "Ask Question")
            }
        }
    }

    @Composable
    private fun QuestionSuggestionsDialog() {
        val suggestions by remember { suggestionsState }
        var showDialog by remember { showSuggestionsDialogState }
        if (showDialog) {
            Dialog(onDismissRequest = { showDialog = false }) {
                Column(modifier = Modifier.background(Color.White).padding(24.dp)) {
                    Text(
                        text = "You might want to ask ...",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(suggestions) {
                            Surface(
                                modifier =
                                    Modifier.background(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(8.dp)
                            ) {
                                Text(
                                    text = it,
                                    fontSize = 12.sp,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier =
                                        Modifier.fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PassageDisplay() {
        val content by remember { contentState }
        Column { Text(text = content, fontSize = 12.sp, modifier = Modifier.padding(16.dp)) }
    }

    override fun onStart() {
        Log.v(TAG, "onStart")
        super.onStart()
        handler.post {
            qaClient.loadModel()
            qaClient.loadDictionary()
        }
        textToSpeech =
            TextToSpeech(this) { status: Int ->
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
        handler.post { qaClient.unload() }
        if (textToSpeech != null) {
            textToSpeech!!.stop()
            textToSpeech!!.shutdown()
        }
    }

    private fun answerQuestion(question: String) {
        var question = question.trim { it <= ' ' }
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
        handler.removeCallbacksAndMessages(null)

        // Hide keyboard and dismiss focus on text edit.
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
        val focusView = currentFocus
        focusView?.clearFocus()

        // Reset content text view
        questionAnswered = false

        // Run TF Lite model to get the answer.
        handler.post {
            val beforeTime = System.currentTimeMillis()
            val answers = qaClient.predict(question, contentState.value)
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
