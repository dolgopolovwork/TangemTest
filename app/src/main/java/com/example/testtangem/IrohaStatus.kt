package com.example.testtangem

import iroha.protocol.Endpoint
import jp.co.soramitsu.iroha.java.TransactionStatusObserver
import jp.co.soramitsu.iroha.java.detail.InlineTransactionStatusObserver
import java.util.concurrent.atomic.AtomicReference


/**
 * Data class that holds information about tx status
 * @param status - status of a transaction
 * @param txException - exception that occurs during transaction commitment
 */
data class IrohaTxStatus(val status: Endpoint.TxStatus?, val txException: Exception?) {

    /**
     * Checks if a transaction is successful
     */
    fun isSuccessful() = txException == null

    companion object {
        /**
         * Creates successful transaction status
         * @return successful status
         */
        fun createSuccessful(status: Endpoint.TxStatus = Endpoint.TxStatus.COMMITTED) =
            IrohaTxStatus(status, null)

        /**
         * Creates failed transaction status
         * @param status- status of transaction
         * @param ex - exception
         * @return failed status
         */
        fun createFailed(status: Endpoint.TxStatus?, ex: Exception) = IrohaTxStatus(status, ex)
    }
}

/**
 * Create tx status observer
 * @param statusReference - reference to an object that will hold tx status after observer completion
 * @return tx status observer
 */
fun createTxStatusObserver(statusReference: AtomicReference<IrohaTxStatus>):
        InlineTransactionStatusObserver.InlineTransactionStatusObserverBuilder {
    return TransactionStatusObserver.builder()
        .onError { ex ->
            statusReference.set(IrohaTxStatus.createFailed(null, IllegalStateException(ex)))
        }
        .onMstExpired { expiredTx ->
            statusReference.set(
                IrohaTxStatus.createFailed(
                    Endpoint.TxStatus.MST_EXPIRED,
                    ToriiErrorResponseException("Tx ${expiredTx.txHash} MST expired. $expiredTx")
                )
            )
        }
        .onNotReceived { failedTx ->
            statusReference.set(
                IrohaTxStatus.createFailed(
                    Endpoint.TxStatus.NOT_RECEIVED,
                    ToriiErrorResponseException("Tx ${failedTx.txHash} was not received. $failedTx")
                )
            )
        }
        .onRejected { rejectedTx ->
            statusReference.set(
                IrohaTxStatus.createFailed(
                    Endpoint.TxStatus.REJECTED,
                    ToriiErrorResponseException("Tx ${rejectedTx.txHash} was rejected. $rejectedTx")
                )
            )
        }
        .onTransactionFailed { failedTx ->
            statusReference.set(
                IrohaTxStatus.createFailed(
                    failedTx.txStatus,
                    ToriiErrorResponseException("Tx ${failedTx.txHash} failed. $failedTx")
                )
            )
        }
        .onUnrecognizedStatus { failedTx ->
            statusReference.set(
                IrohaTxStatus.createFailed(
                    Endpoint.TxStatus.UNRECOGNIZED,
                    ToriiErrorResponseException("Tx ${failedTx.txHash} got unrecognized status. $failedTx")
                )
            )
        }
        .onTransactionCommitted {
            statusReference.set(IrohaTxStatus.createSuccessful())
        }
}

/**
 * Exception class that holds error message alongside with [ToriiResponse]
 */
class ToriiErrorResponseException(message: String) :
    java.lang.Exception(message)