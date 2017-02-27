package com.iota.iri.service.storage;

import com.iota.iri.model.Hash;
import com.iota.iri.model.Transaction;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Created by Adrian on 24.02.2017.
 */
public interface IStorageProvider {

    // helper methods
    void init() throws IOException;

    void shutdown();

    // trx
    List<Transaction> getTagTransactions(final byte[] hash);

    List<Transaction> getAddressTransactions(final byte[] hash);

    List<Transaction> getApproveeTransactions(final byte[] hash);

    List<Transaction> getBundleTransactions(final byte[] hash);

    List<Transaction> getTagsTransactions(final byte[] hash);

    Transaction getTransaction(final byte[] hash);

    long storeTransaction(final byte[] hash, final Transaction transaction, final boolean tip);

    void setTransactionValidity(final byte[] hash, final int validity);

    List<Hash> tips();

    boolean tipFlag(final byte[] hash);

    // scratchpad
    ByteBuffer getAnalyzedTransactionsFlags();

    int getNumberOfTransactionsToRequest();

    void transactionToRequest(final byte[] buffer, final int offset);

    void clearAnalyzedTransactionsFlags();

    boolean analyzedTransactionFlag(long pointer);

    boolean setAnalyzedTransactionFlag(long pointer);

    void saveAnalyzedTransactionsFlags();

    void loadAnalyzedTransactionsFlags();
}