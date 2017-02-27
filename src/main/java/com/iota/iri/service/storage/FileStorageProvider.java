package com.iota.iri.service.storage;

import com.iota.iri.model.Hash;
import com.iota.iri.model.Transaction;
import com.iota.iri.service.storage.file.Storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Created by Adrian on 26.02.2017.
 */
public class FileStorageProvider implements IStorageProvider{

    @Override
    public void init() throws IOException{
        Storage.instance().init();
    }

    @Override
    public void shutdown() {
        Storage.instance().shutdown();
    }

    @Override
    public List<Transaction> getTagTransactions(byte[] hash) {
        return null;
    }

    @Override
    public List<Transaction> getAddressTransactions(byte[] hash) {
        return null;
    }

    @Override
    public List<Transaction> getApproveeTransactions(byte[] hash) {
        return null;
    }

    @Override
    public List<Transaction> getBundleTransactions(byte[] hash) {
        return null;
    }

    @Override
    public List<Transaction> getTagsTransactions(byte[] hash) {
        return null;
    }

    @Override
    public Transaction getTransaction(byte[] hash) {
        return null;
    }

    @Override
    public long storeTransaction(byte[] hash, Transaction transaction, boolean tip) {
        return 0;
    }

    @Override
    public void setTransactionValidity(byte[] hash, int validity) {

    }

    @Override
    public List<Hash> tips() {
        return null;
    }

    @Override
    public boolean tipFlag(byte[] hash) {
        return false;
    }

    @Override
    public ByteBuffer getAnalyzedTransactionsFlags() {
        return null;
    }

    @Override
    public int getNumberOfTransactionsToRequest() {
        return 0;
    }

    @Override
    public void transactionToRequest(byte[] buffer, int offset) {

    }

    @Override
    public void clearAnalyzedTransactionsFlags() {

    }

    @Override
    public boolean analyzedTransactionFlag(long pointer) {
        return false;
    }

    @Override
    public boolean setAnalyzedTransactionFlag(long pointer) {
        return false;
    }

    @Override
    public void saveAnalyzedTransactionsFlags() {

    }

    @Override
    public void loadAnalyzedTransactionsFlags() {

    }


}
