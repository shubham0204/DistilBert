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
import android.content.res.AssetManager
import android.util.Log
import androidx.annotation.WorkerThread
import com.google.common.base.Joiner
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/** Interface to load TfLite model and provide predictions.  */
class QaClient(private val context: Context) {

    private val TAG = "BertDemo"
    private val MODEL_PATH = "model_bert_medium_512_quant.tflite"
    private val DIC_PATH = "vocab.txt"
    private val MAX_ANS_LEN = 32
    private val MAX_QUERY_LEN = 64
    private val MAX_SEQ_LEN = 512
    private val DO_LOWER_CASE = true
    private val PREDICT_ANS_NUM = 5
    private val NUM_LITE_THREADS = 7
    // Need to shift 1 for outputs ([CLS]).
    private val OUTPUT_OFFSET = 1
    private val SPACE_JOINER = Joiner.on(" ")

    /** Convert the answer back to original text form.  */
    private val dic: MutableMap<String, Int> = HashMap()
    private val featureConverter: FeatureConverter =
        FeatureConverter(dic, DO_LOWER_CASE, MAX_QUERY_LEN, MAX_SEQ_LEN)
    private var tflite: Interpreter? = null

    @WorkerThread
    private fun convertBack(
        feature: Feature,
        start: Int,
        end: Int
    ): String {
        // Shifted index is: index of logits + offset.
        val shiftedStart = start + OUTPUT_OFFSET
        val shiftedEnd = end + OUTPUT_OFFSET
        val startIndex = feature.tokenToOrigMap[shiftedStart]!!
        val endIndex = feature.tokenToOrigMap[shiftedEnd]!!
        // end + 1 for the closed interval.
        return SPACE_JOINER.join(feature.origTokens.subList(startIndex, endIndex + 1))
    }

    @WorkerThread
    @Synchronized
    fun loadModel() {
        try {
            val buffer: ByteBuffer = loadModelFile(context.assets)
            val opt = Interpreter.Options()
            opt.setNumThreads(NUM_LITE_THREADS)
            tflite = Interpreter(buffer, opt)
            Log.v(TAG, "TFLite model loaded.")
        } catch (ex: IOException) {
            Log.e(TAG, ex.message!!)
        }
    }

    @WorkerThread
    @Synchronized
    fun loadDictionary() {
        try {
            loadDictionaryFile(context.assets)
            Log.v(TAG, "Dictionary loaded.")
        } catch (ex: IOException) {
            Log.e(TAG, ex.message!!)
        }
    }

    @WorkerThread
    @Synchronized
    fun unload() {
        tflite!!.close()
        dic.clear()
    }

    /** Load tflite model from assets.  */
    @Throws(IOException::class)
    fun loadModelFile(assetManager: AssetManager): MappedByteBuffer {
        assetManager.openFd(MODEL_PATH).use { fileDescriptor ->
            FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
                val fileChannel = inputStream.channel
                val startOffset = fileDescriptor.startOffset
                val declaredLength = fileDescriptor.declaredLength
                return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            }
        }
    }

    /** Load dictionary from assets.  */
    @Throws(IOException::class)
    fun loadDictionaryFile(assetManager: AssetManager) {
        assetManager.open(DIC_PATH).use { ins ->
            BufferedReader(InputStreamReader(ins)).use { reader ->
                var index = 0
                while (reader.ready()) {
                    val key = reader.readLine()
                    dic[key] = index++
                }
            }
        }
    }

    /**
     * Input: Original content and query for the QA task. Later converted to Feature by
     * FeatureConverter. Output: A String[] array of answers and a float[] array of corresponding
     * logits.
     */
    @WorkerThread
    @Synchronized
    fun predict(query: String, content: String): List<QaAnswer> {
        Log.v(TAG, "TFLite model: $MODEL_PATH running...")
        Log.v(TAG, "Convert Feature...")
        val feature = featureConverter.convert(query, content)
        Log.v(TAG, "Set inputs...")
        val inputIds = Array(1) { IntArray(MAX_SEQ_LEN) }
        val inputMask = Array(1) { IntArray(MAX_SEQ_LEN) }
        val segmentIds = Array(1) { IntArray(MAX_SEQ_LEN) }
        val startLogits = Array(1) { FloatArray(MAX_SEQ_LEN) }
        val endLogits = Array(1) { FloatArray(MAX_SEQ_LEN) }
        for (j in 0 until MAX_SEQ_LEN) {
            inputIds[0][j] = feature.inputIds[j]
            inputMask[0][j] = feature.inputMask[j]
            segmentIds[0][j] = feature.segmentIds[j]
        }
        val inputs = arrayOf<Any>(inputIds, inputMask, segmentIds)
        val output: MutableMap<Int, Any> = HashMap()
        // Arrange outputs based on what Netron.app spits out.
        output[0] = endLogits
        output[1] = startLogits
        Log.v(TAG, "Run inference...")
        /*Tensor inputTensor0 = tflite.getInputTensor(0);
    printTensorDump(inputTensor0);
    Tensor inputTensor1 = tflite.getInputTensor(1);
    printTensorDump(inputTensor1);
    Tensor inputTensor2 = tflite.getInputTensor(2);
    printTensorDump(inputTensor2);
    // Create output tensor
    Tensor outputTensor = tflite.getOutputTensor(0);
    printTensorDump(outputTensor);
    Tensor outputTensor1 = tflite.getOutputTensor(1);
    printTensorDump(outputTensor1);*/tflite!!.runForMultipleInputsOutputs(inputs, output)
        Log.v(TAG, "Convert answers...")
        val answers = getBestAnswers(startLogits[0], endLogits[0], feature)
        Log.v(TAG, "Finish.")
        return answers
    }

    private fun printTensorDump(tensor: Tensor) {
        Log.d(TAG, "  shape.length: " + tensor.shape().size)
        var i = 0
        val var10000 = tensor.shape()
        val var3 = var10000.size
        while (i < var3) {
            Log.d(TAG, "    shape[" + i + "]: " + tensor.shape()[i])
            ++i
        }
        Log.d(TAG, "  dataType: " + tensor.dataType())
        Log.d(TAG, "  name: " + tensor.name())
        Log.d(TAG, "  numBytes: " + tensor.numBytes())
        Log.d(TAG, "  index: " + tensor.index())
        Log.d(TAG, "  numDimensions: " + tensor.numDimensions())
        Log.d(TAG, "  numElements: " + tensor.numElements())
        Log.d(TAG, "  shapeSignature.length: " + tensor.shapeSignature().size)
        var var4 = TAG
        var var10001 = StringBuilder().append("  quantizationParams.getScale: ")
        var var10002 = tensor.quantizationParams()
        Log.d(var4, var10001.append(var10002.scale).toString())
        var4 = TAG
        var10001 = StringBuilder().append("  quantizationParams.getZeroPoint: ")
        var10002 = tensor.quantizationParams()
        Log.d(var4, var10001.append(var10002.zeroPoint).toString())
        Log.d(TAG, "==================================================================")
    }

    /** Find the Best N answers & logits from the logits array and input feature.  */
    @Synchronized
    private fun getBestAnswers(
        startLogits: FloatArray, endLogits: FloatArray, feature: Feature
    ): List<QaAnswer> {
        // Model uses the closed interval [start, end] for indices.
        val startIndexes = getBestIndex(startLogits, feature.tokenToOrigMap)
        val endIndexes = getBestIndex(endLogits, feature.tokenToOrigMap)
        val origResults: MutableList<QaAnswer.Pos> = ArrayList()
        for (start in startIndexes) {
            for (end in endIndexes) {
                if (end < start) {
                    continue
                }
                val length = end - start + 1
                if (length > MAX_ANS_LEN) {
                    continue
                }
                origResults.add(QaAnswer.Pos(start, end, startLogits[start] + endLogits[end]))
            }
        }
        origResults.sort()
        val answers: MutableList<QaAnswer> = ArrayList()
        for (i in origResults.indices) {
            if (i >= PREDICT_ANS_NUM) {
                break
            }
            val convertedText: String = if (origResults[i].start > 0) {
                convertBack(feature, origResults[i].start, origResults[i].end)
            } else {
                ""
            }
            val ans = QaAnswer(convertedText, origResults[i])
            answers.add(ans)
        }
        return answers
    }

    /** Get the n-best logits from a list of all the logits.  */
    @WorkerThread
    @Synchronized
    private fun getBestIndex(logits: FloatArray, tokenToOrigMap: Map<Int, Int>): IntArray {
        val tmpList: MutableList<QaAnswer.Pos> = ArrayList()
        for (i in 0 until MAX_SEQ_LEN) {
            if (tokenToOrigMap.containsKey(i + OUTPUT_OFFSET)) {
                tmpList.add(QaAnswer.Pos(i, i, logits[i]))
            }
        }
        tmpList.sort()
        val indexes = IntArray(PREDICT_ANS_NUM)
        for (i in 0 until PREDICT_ANS_NUM) {
            indexes[i] = tmpList[i].start
        }
        return indexes
    }

}
