package com.example.testtangem

import com.github.kittinunf.result.Result
import iroha.protocol.Endpoint
import iroha.protocol.TransactionOuterClass
import jp.co.soramitsu.iroha.java.IrohaAPI
import jp.co.soramitsu.iroha.java.Utils
import jp.co.soramitsu.iroha.java.subscription.WaitForTerminalStatus
import java.util.concurrent.atomic.AtomicReference

/**
 * Statuses that we consider terminal
 */
val terminalStatuses = listOf(
    Endpoint.TxStatus.STATELESS_VALIDATION_FAILED,
    Endpoint.TxStatus.STATEFUL_VALIDATION_FAILED,
    Endpoint.TxStatus.COMMITTED,
    Endpoint.TxStatus.MST_EXPIRED,
    //We don't consider this status terminal on purpose
    //Endpoint.TxStatus.NOT_RECEIVED,
    Endpoint.TxStatus.REJECTED,
    Endpoint.TxStatus.UNRECOGNIZED
)

/**
 * Endpoint of Iroha to write transactions
 * @param irohaAPI Iroha network
 */
open class IrohaConsumer(private val irohaAPI: IrohaAPI) {

    private val waitForTerminalStatus = WaitForTerminalStatus(terminalStatuses)

    /**
     * Send transaction to Iroha and check if it is committed
     * @param tx - built protobuf iroha transaction
     * @return hex representation of hash or failure
     */
    fun send(tx: TransactionOuterClass.Transaction): Result<String, Exception> {
        return Result.of {
            val statusReference = AtomicReference<IrohaTxStatus>()
            irohaAPI.transaction(tx, waitForTerminalStatus)
                .blockingSubscribe(createTxStatusObserver(statusReference).build())
            if (statusReference.get().isSuccessful()) {
                Utils.toHex(Utils.hash(tx))
            } else {
                throw statusReference.get().txException!!
            }
        }
    }
}