package com.example.testtangem

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.tangem.CardManager
import com.tangem.commands.Card
import com.tangem.tangem_sdk_new.DefaultCardManagerDelegate
import com.tangem.tangem_sdk_new.NfcLifecycleObserver
import com.tangem.tangem_sdk_new.nfc.NfcManager
import com.tangem.tasks.ScanEvent
import com.tangem.tasks.TaskEvent
import iroha.protocol.Primitive
import iroha.protocol.TransactionOuterClass
import jp.co.soramitsu.iroha.java.IrohaAPI
import jp.co.soramitsu.iroha.java.TransactionBuilder
import jp.co.soramitsu.iroha.java.Utils
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast
import org.jetbrains.anko.uiThread
import javax.xml.bind.DatatypeConverter


private const val TAG = "Tangem test"

class MainActivity : AppCompatActivity() {

    // Test user public key. Its corresponding private key is stored on the card which id ends with '9'
    private val publicKey =
        DatatypeConverter.parseHexBinary("e34857cd8cf1e1d82efd30d606db6b29d8b92ce684b299f48f40d1d7f40cd531")
    private val nfcManager = NfcManager()
    private val cardManagerDelegate: DefaultCardManagerDelegate =
        DefaultCardManagerDelegate(nfcManager.reader)
    private val cardManager = CardManager(nfcManager.reader, cardManagerDelegate)

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        nfcManager.setCurrentActivity(this)
        cardManagerDelegate.activity = this
        lifecycle.addObserver(NfcLifecycleObserver(nfcManager))
        val scanButton: Button = findViewById(R.id.scanButton)!!
        val signButton: Button = findViewById(R.id.signButton)!!
        val irohaIpAddressEditText: EditText = findViewById(R.id.ipAddress)
        var card: Card? = null
        // First, we have to scan the card to get its id and public key
        scanButton.setOnClickListener { _ ->
            cardManager.scanCard { taskEvent ->
                when (taskEvent) {
                    is TaskEvent.Event -> {
                        when (taskEvent.data) {
                            is ScanEvent.OnReadEvent -> {
                                // Handle returned card data
                                card = (taskEvent.data as ScanEvent.OnReadEvent).card
                            }
                        }
                    }
                }
            }
        }
        // Then, we create and sign a transaction
        signButton.setOnClickListener { _ ->
            if (card == null) {
                toast("Please, scan your card first")
                return@setOnClickListener
            } else if (irohaIpAddressEditText.text.toString().isEmpty()) {
                toast("Please, set Iroha node IP address")
                return@setOnClickListener
            }
            // Create tx
            val unsignedTransaction = createTransaction()
            // Sign it
            cardManager.sign(
                arrayOf(unsignedTransaction.payload()),
                card!!.cardId
            ) {
                when (it) {
                    is TaskEvent.Completion -> {
                        if (it.error != null) runOnUiThread {
                            Log.e(TAG, it.error!!.message ?: "Error occurred")
                            toast("Error occurred")
                        }
                    }
                    is TaskEvent.Event -> runOnUiThread {
                        val signature = formSignature(it.data.signature)
                        val signedTx = unsignedTransaction.addSignature(signature).build()
                        signButton.isEnabled = false
                        sendTransactionToIroha(
                            irohaIpAddressEditText.text.toString(),
                            signedTx,
                            {
                                signButton.isEnabled = true
                                toast("Transaction has been sent")
                            },
                            {
                                signButton.isEnabled = true
                                toast("Cannot send transaction")
                            })
                    }
                }
            }
        }
    }

    /**
     * Sends a transaction to Iroha node
     * @param irohaIPAddress - Iroha IP address
     * @param transaction - transaction to send
     * @param onSuccess - function that is executed if a given tx is successfully committed
     * @param onFail - function that is executed on error
     */
    private fun sendTransactionToIroha(
        irohaIPAddress: String,
        transaction: TransactionOuterClass.Transaction,
        onSuccess: () -> Unit,
        onFail: () -> Unit
    ) {
        //TODO this thing must be closed properly
        val iroha = IrohaAPI(irohaIPAddress, 50051)
        val irohaConsumer = IrohaConsumer(iroha)
        doAsync {
            irohaConsumer.send(transaction).fold({ uiThread { onSuccess() } },
                { ex ->
                    Log.e(TAG, "Cannot send transaction to Iroha", ex)
                    uiThread { onFail() }
                })
        }
    }

    /**
     * Creates a simple `SetAccountDetail` transaction
     * @return unsigned `SetAccountDetail` transaction
     */
    private fun createTransaction() = TransactionBuilder("test@d3", System.currentTimeMillis())
        .setAccountDetail("test@d3", "time", System.currentTimeMillis().toString())
        .build()

    /**
     * Creates a signature object
     * @param signatureBytes - signature bytes
     * @return signature
     */
    private fun formSignature(signatureBytes: ByteArray): Primitive.Signature {
        return Primitive.Signature.newBuilder()
            .setSignature(Utils.toHex(signatureBytes))
            .setPublicKey(Utils.toHex(publicKey))
            .build()
    }
}
