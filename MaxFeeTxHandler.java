import java.util.HashSet;
import java.util.ArrayList;

public class MaxFeeTxHandler {
    private UTXOPool currentUTXOPool;

    /*
     * Initializes the public ledger with the current UTXOPool (collection of unspent
     * transaction outputs). This creates a defensive copy of utxoPool using the 
     * UTXOPool(UTXOPool uPool) constructor.
     */
    public MaxFeeTxHandler(UTXOPool utxoPool) {
        this.currentUTXOPool = new UTXOPool(utxoPool);
    }

    /*
     * Validates the transaction based on the following criteria:
     * (1) All outputs claimed by tx are in the current UTXO pool.
     * (2) The signatures on each input of tx are valid.
     * (3) No UTXO is claimed multiple times by tx.
     * (4) All of tx’s output values are non-negative.
     * (5) The sum of tx’s input values is greater than or equal to the sum of 
     * its output values.
     * Returns true if valid, false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        HashSet<UTXO> usedUTXOs = new HashSet<>();
        double totalInputs = 0.0;
        double totalOutputs = 0.0;

        // Check all inputs of the transaction
        for (int i = 0; i < tx.getInputs().size(); i++) {
            Transaction.Input currentInput = tx.getInput(i);
            UTXO currentUTXO = new UTXO(currentInput.prevTxHash, currentInput.outputIndex);

            // Validate the inputs against the criteria
            if (!this.currentUTXOPool.contains(currentUTXO) ||
                usedUTXOs.contains(currentUTXO) || 
                !currentUTXOPool.getTxOutput(currentUTXO).address.verifySignature(tx.getRawDataToSign(i), currentInput.signature)) {
                return false; // Transaction is invalid
            }

            // Calculate total input values
            totalInputs += currentUTXOPool.getTxOutput(currentUTXO).value;
            usedUTXOs.add(currentUTXO);
        }

        // Validate outputs
        for (Transaction.Output output : tx.getOutputs()) {
            if (output.value < 0) {
                return false; // Output values must be non-negative
            }
            totalOutputs += output.value;
        }

        // Check if total inputs are sufficient compared to total outputs
        return totalInputs >= totalOutputs;
    }

    /*
     * Processes an epoch by evaluating an array of proposed transactions,
     * validating each transaction, and updating the UTXO pool as necessary.
     * Returns an array of accepted transactions that maximizes total transaction fees.
     */
    public Transaction[] handleTxs(Transaction[] proposedTxs) {
        ArrayList<Transaction> acceptedTxs = new ArrayList<>();
        double maxTotalFee = 0;
        ArrayList<Transaction> bestTxSet = new ArrayList<>();

        // Evaluate all combinations of transactions to find the set with max total fees
        int numTxs = proposedTxs.length;
        int maxCombinations = 1 << numTxs; // 2^numTxs combinations

        for (int i = 0; i < maxCombinations; i++) {
            ArrayList<Transaction> currentTxSet = new ArrayList<>();
            double currentTotalFee = 0;

            for (int j = 0; j < numTxs; j++) {
                if ((i & (1 << j)) > 0) { // If the j-th transaction is included in the combination
                    Transaction tx = proposedTxs[j];
                    if (isValidTx(tx)) {
                        currentTxSet.add(tx);
                        currentTotalFee += getTransactionFee(tx);
                    } else {
                        currentTxSet.clear(); // Clear the set if any invalid transaction is found
                        break;
                    }
                }
            }

            // Check if the current set of transactions has a higher total fee
            if (currentTotalFee > maxTotalFee) {
                maxTotalFee = currentTotalFee;
                bestTxSet = currentTxSet;
            }
        }

        // Update UTXO pool with the best set of transactions
        for (Transaction tx : bestTxSet) {
            acceptedTxs.add(tx);
            for (Transaction.Input input : tx.getInputs()) {
                UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
                this.currentUTXOPool.removeUTXO(utxo);
            }

            byte[] transactionHash = tx.getHash();
            for (int j = 0; j < tx.getOutputs().size(); j++) {
                UTXO newUTXO = new UTXO(transactionHash, j);
                this.currentUTXOPool.addUTXO(newUTXO, tx.getOutput(j));
            }
        }

        return acceptedTxs.toArray(new Transaction[0]);
    }

    // Helper method to calculate the transaction fee
    private double getTransactionFee(Transaction tx) {
        double inputSum = 0.0;
        double outputSum = 0.0;

        for (Transaction.Input input : tx.getInputs()) {
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            if (currentUTXOPool.contains(utxo)) {
                inputSum += currentUTXOPool.getTxOutput(utxo).value;
            }
        }

        for (Transaction.Output output : tx.getOutputs()) {
            outputSum += output.value;
        }

        return inputSum - outputSum; // Returns the fee
    }
}
