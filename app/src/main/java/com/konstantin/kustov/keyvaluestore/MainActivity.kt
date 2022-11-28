package com.konstantin.kustov.keyvaluestore

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import com.konstantin.kustov.keyvaluestore.Constants.BEGIN
import com.konstantin.kustov.keyvaluestore.Constants.COMMIT
import com.konstantin.kustov.keyvaluestore.Constants.COUNT
import com.konstantin.kustov.keyvaluestore.Constants.DELETE
import com.konstantin.kustov.keyvaluestore.Constants.GET
import com.konstantin.kustov.keyvaluestore.Constants.KEY_VALUE_STORE_TAG
import com.konstantin.kustov.keyvaluestore.Constants.ROLLBACK
import com.konstantin.kustov.keyvaluestore.Constants.SET
import com.konstantin.kustov.keyvaluestore.Constants.commandLinePrefix
import java.util.Stack

class MainActivity : AppCompatActivity() {

    private val transactionsStack = Stack<HashMap<String, String>>()
    private val basicStore = hashMapOf<String, String>()
    private var commandWindow: TextView? = null
    private var commandLine: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        commandWindow = findViewById(R.id.commandWindow)
        commandLine = findViewById<EditText?>(R.id.commandLine)?.apply {
            setText(commandLinePrefix)
            doAfterTextChanged(::handlePrefixForInput)
            setOnEditorActionListener(::handleGoActionForInput)
            requestFocus()
        }
    }

    private fun handlePrefixForInput(text: Editable?) {
        if (text?.startsWith(commandLinePrefix) == false) {
            val textWithPrefix = commandLinePrefix
            commandLine?.setText(textWithPrefix)
            commandLine?.setSelection(commandLine?.text?.length ?: 0)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun handleGoActionForInput(
        textView: TextView,
        actionId: Int,
        event: KeyEvent
    ): Boolean {
        if (actionId == EditorInfo.IME_ACTION_GO) {
            handleInput()
            commandLine?.setText(commandLinePrefix)
            commandLine?.setSelection(commandLine?.text?.length ?: 0)
            return true
        }
        return false
    }

    private fun handleInput() {
        val windowText: String = commandWindow?.text?.toString().orEmpty()
        var commandLineText: String = commandLine?.text?.toString().orEmpty()
        val inputWithoutPrefix = commandLineText.removePrefix(commandLinePrefix)
        val inputPhrase = inputWithoutPrefix.split(" ")

        commandLineText = when {

            inputWithoutPrefix.startsWith(SET, true) ->
                if (inputPhrase.size == 3) {
                    val key = inputPhrase[1]
                    val value = inputPhrase[2]
                    getTopTransaction()?.set(key, value)
                    "$commandLineText\n${getString(R.string.added)}"
                } else {
                    "$commandLineText\n${getString(R.string.command_does_not_exist)}"
                }

            inputWithoutPrefix.startsWith(GET, true) ->
                if (inputPhrase.size == 2) {
                    val key = inputPhrase[1]
                    val valueToHistory = getNestedElementByKey(key)
                    if (valueToHistory.isNullOrBlank()) "$commandLineText\n${getString(R.string.key_not_set)}"
                    else "$commandLineText\n$valueToHistory"
                } else {
                    "$commandLineText\n${getString(R.string.command_does_not_exist)}"
                }

            inputWithoutPrefix.startsWith(DELETE, true) ->
                if (inputPhrase.size == 2) {
                    val key = inputPhrase[1]
                    val valueToHistory = getTopTransaction()?.remove(key).orEmpty()
                    if (valueToHistory.isBlank()) "$commandLineText\n${getString(R.string.no_such_value)}"
                    else "$commandLineText\n${getString(R.string.deleted)} \"$key $valueToHistory\""
                } else {
                    "$commandLineText\n${getString(R.string.command_does_not_exist)}"
                }

            inputWithoutPrefix.startsWith(COUNT, true) ->
                if (inputPhrase.size == 2) {
                    val valueToFind = inputPhrase[1]
                    val valuesCountToHistory = getNestedCountByValue(valueToFind)
                    if (valuesCountToHistory == 0) "$commandLineText\n${getString(R.string.values_not_found)}"
                    else "$commandLineText\n$valuesCountToHistory"
                } else {
                    "$commandLineText\n${getString(R.string.command_does_not_exist)}"
                }

            inputWithoutPrefix.startsWith(BEGIN, true) -> {
                transactionsStack.push(hashMapOf())
                commandLineText
            }

            inputWithoutPrefix.startsWith(COMMIT, true) ->
                if (transactionsStack.isEmpty()) {
                    "$commandLineText\n${getString(R.string.no_transaction)}"
                } else {
                    val topTransaction = transactionsStack.last()
                    transactionsStack.pop()

                    topTransaction?.forEach { (key, value) ->
                        if (transactionsStack.isEmpty()) basicStore[key] = value
                        else transactionsStack.last()?.set(key, value)
                    }
                    "$commandLineText\n${getString(R.string.last_transaction_committed)}"
                }

            inputWithoutPrefix.startsWith(ROLLBACK, true) ->
                if (transactionsStack.isEmpty()) {
                    "$commandLineText\n${getString(R.string.no_transaction)}"
                } else {
                    transactionsStack.pop()
                    "$commandLineText\n${getString(R.string.last_transaction_removed)}"
                }

            else -> {
                if (commandLineText == commandLinePrefix) {
                    "$commandLineText\n${getString(R.string.please_enter_the_command)}"
                } else {
                    "$commandLineText\n${getString(R.string.command_does_not_exist)}"
                }
            }

        }

        Log.d(KEY_VALUE_STORE_TAG, "basicStore = $basicStore")
        Log.d(KEY_VALUE_STORE_TAG, "transactionsStack = ${transactionsStack.elements().toList()}")

        val newHistoryText = when {
            windowText.isBlank() && commandLineText.isNotBlank() -> commandLineText
            windowText.isNotBlank() && commandLineText.isNotBlank() -> "$windowText\n$commandLineText"
            else -> windowText
        }
        commandWindow?.text = newHistoryText
    }

    private fun getTopTransaction(): HashMap<String, String>? {
        return if (transactionsStack.isEmpty()) {
            basicStore
        } else {
            transactionsStack.peek()
        }
    }

    private fun getNestedElementByKey(key: String): String? {
        var value: String? = null
        if (basicStore.keys.contains(key)) {
            value = basicStore[key]
        }
        transactionsStack.elements().toList().forEach {
            if (it.keys.contains(key)) {
                value = it[key]
            }
        }
        return value
    }

    private fun getNestedCountByValue(value: String): Int {
        val elements = hashMapOf<String, String>()
        var count = 0

        basicStore.forEach {
            elements[it.key] = it.value
        }
        transactionsStack.elements().toList().forEach {
            it.forEach { item ->
                elements[item.key] = item.value
            }
        }
        elements.forEach {
            if (it.value == value) {
                count++
            }
        }

        return count
    }

}