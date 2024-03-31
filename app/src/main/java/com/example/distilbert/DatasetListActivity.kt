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

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.distilbert.ml.LoadDatasetClient
import com.example.distilbert.ui.theme.DistilBertTheme

/**
 * An activity representing a list of Datasets. This activity has different presentations for
 * handset and tablet-size devices. On handsets, the activity presents a list of items, which when
 * touched, lead to a [QaActivity] representing item details. On tablets, the activity
 * presents the list of items and item details side-by-side using two vertical panes.
 */
class DatasetListActivity : ComponentActivity() {

    private val docsState: MutableState<Array<String>> = mutableStateOf(emptyArray())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ActivityUI()
        }

    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ActivityUI() {
        DistilBertTheme {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            ) {

                Scaffold(topBar = {
                    TopAppBar(colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ), title = {
                        Text("Question Answering")
                    })
                }) { paddingValues ->
                    DocumentsList(modifier = Modifier.padding(paddingValues))
                }
            }
        }
    }

    @Composable
    private fun DocumentsList(modifier: Modifier = Modifier) {
        val context = LocalContext.current
        val docs by remember { docsState }
        LaunchedEffect(Unit) {
            val datasetClient = LoadDatasetClient(context)
            docsState.value = datasetClient.titles
        }
        Column {
            LazyColumn(modifier = modifier.weight(1f)) {
                itemsIndexed(docs) { index, item ->
                    Text(
                        text = item,
                        modifier = Modifier
                            .clickable {
                                startActivity(QaActivity.newInstance(context, index))
                            }
                            .padding(16.dp)
                            .fillMaxWidth()
                    )
                }
            }
            Row( modifier = Modifier
                .background(MaterialTheme.colorScheme.tertiary)
                .padding(24.dp)
                .fillMaxWidth()
            ) {
                Text(
                    text = "Select a passage to continue",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Proceed",
                    tint = Color.White
                )
            }
        }
    }

}

