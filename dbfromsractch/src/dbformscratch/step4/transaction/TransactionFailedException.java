package dbformscratch.step4.transaction;

public class TransactionFailedException extends RuntimeException {

    private final TransactionContext transaction;

    public TransactionFailedException(
            TransactionContext transaction,
            RuntimeException cause
    ) {
        super(
                "Transaction TX-" + transaction.getTransactionId()
                        + " rolled back: " + cause.getMessage(),
                cause
        );
        this.transaction = transaction;
    }

    public TransactionContext getTransaction() {
        return transaction;
    }
}
