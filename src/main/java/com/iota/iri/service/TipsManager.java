package com.iota.iri.service;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.Bundle;
import com.iota.iri.Milestone;
import com.iota.iri.Snapshot;
import com.iota.iri.model.Hash;
import com.iota.iri.model.Transaction;
import com.iota.iri.service.storage.AbstractStorage;
import com.iota.iri.service.storage.Storage;
import com.iota.iri.service.storage.StorageAddresses;
import com.iota.iri.service.storage.StorageApprovers;
import com.iota.iri.service.storage.StorageTransactions;
import com.iota.iri.utils.Converter;

public class TipsManager {

    private static final Logger log = LoggerFactory.getLogger(TipsManager.class);

    private static int RATING_THRESHOLD = 75; // Must be in [0..100] range
    
    private static int ARTIFICAL_LATENCY = 60; // in seconds 

    static boolean shuttingDown;

    static int numberOfConfirmedTransactions;
    
    static final byte[] voidBundle = new byte[Transaction.BUNDLE_SIZE];

    static final byte[] analyzedTransactionsFlags = new byte[134217728];
    static final byte[] analyzedTransactionsFlagsCopy = new byte[134217728];
    static final byte[] zeroedAnalyzedTransactionsFlags = new byte[134217728];

    public class TransactionSummary {
        Hash tx_hash;
        Hash tx_address;
        long trunk_pointer;
        long branch_pointer;
        long arrivalTime;
        long currentIndex;
        byte[] bundle;
        boolean bundleUpdated;
    }
    
    public class MilestoneSummary {
        public long arrivalTime;
        public long pointer;
    }
    
    static public Hashtable<Long,TransactionSummary> transactionSummaryTable = new Hashtable<>(200000);
    
    static public Hashtable<ByteBuffer,Bundle> bundleTable = new Hashtable<>(200000);
    
    static public Hashtable<Integer,MilestoneSummary> milestoneSummaryTable = new Hashtable<>(10000);
    
    public static void setRATING_THRESHOLD(int value) {
        if (value < 0) value = 0;
        if (value > 100) value = 100;
        RATING_THRESHOLD = value;
    }
    
    public static void setARTIFICAL_LATENCY(int value) {
        ARTIFICAL_LATENCY = value;
    }
    
    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
    
    public void init() {

        // Fill the runtime tangle objects
        
        for (final Long pointer : StorageAddresses.instance().addressesOf(Milestone.COORDINATOR)) {
            final Transaction transaction = StorageTransactions.instance().loadTransaction(pointer);
            if (transaction.currentIndex == 0) {
                int milestoneIndex = (int) Converter.longValue(transaction.trits(), Transaction.TAG_TRINARY_OFFSET, 15);
                long itsArrivalTime = transaction.arrivalTime;
                MilestoneSummary milestoneSummary = new MilestoneSummary();
                milestoneSummary.arrivalTime = itsArrivalTime;
                milestoneSummary.pointer = pointer;
                milestoneSummaryTable.put(milestoneIndex, milestoneSummary);
            }
        }
        
        long pointer = AbstractStorage.CELLS_OFFSET - AbstractStorage.SUPER_GROUPS_OFFSET;
        log.info("Loading transaction summaries");
        int txCounter = 0;
        while (pointer < StorageTransactions.transactionsNextPointer) {
            Transaction transaction = StorageTransactions.instance().loadTransaction(pointer);
            if (transaction.type != Storage.PREFILLED_SLOT) {
                txCounter++;
                TipsManager.TransactionSummary tx_summary = TipsManager.instance().new TransactionSummary();
                tx_summary.tx_hash = new Hash(transaction.hash, 0, Transaction.HASH_SIZE);
                tx_summary.tx_address = new Hash(transaction.address);
                tx_summary.trunk_pointer = transaction.trunkTransactionPointer;
                tx_summary.branch_pointer = transaction.branchTransactionPointer;
                tx_summary.arrivalTime = transaction.arrivalTime;
                tx_summary.currentIndex = transaction.currentIndex;
                tx_summary.bundle = new byte[Transaction.BUNDLE_SIZE];
                tx_summary.bundleUpdated = true;
                System.arraycopy(transaction.bundle, 0, tx_summary.bundle, 0, Transaction.BUNDLE_SIZE);
                TipsManager.transactionSummaryTable.put(pointer, tx_summary);
            }
            pointer += AbstractStorage.CELL_SIZE;
        }
        log.info("Loading transaction summaries has finished, txCounter = {}  {}",txCounter,TipsManager.transactionSummaryTable.size());
        
        (new Thread(() -> {
            
            final SecureRandom rnd = new SecureRandom();

            while (!shuttingDown) {
                
                try {
                    final int previousLatestMilestoneIndex = Milestone.latestMilestoneIndex;
                    final int previousSolidSubtangleLatestMilestoneIndex = Milestone.latestSolidSubtangleMilestoneIndex;

                    Milestone.updateLatestMilestone();
                    Milestone.updateLatestSolidSubtangleMilestone();

                    if (previousLatestMilestoneIndex != Milestone.latestMilestoneIndex) {

                        log.info("Latest milestone has changed from #" + previousLatestMilestoneIndex
                                + " to #" + Milestone.latestMilestoneIndex);
                    }
                    if (previousSolidSubtangleLatestMilestoneIndex != Milestone.latestSolidSubtangleMilestoneIndex) {

                        log.info("Latest SOLID SUBTANGLE milestone has changed from #"
                                + previousSolidSubtangleLatestMilestoneIndex + " to #"
                                + Milestone.latestSolidSubtangleMilestoneIndex);
                    }

                    long latency = 5000;
                    if (Milestone.latestSolidSubtangleMilestoneIndex > Milestone.MILESTONE_START_INDEX &&
                            Milestone.latestMilestoneIndex == Milestone.latestSolidSubtangleMilestoneIndex) {
                        latency = (long)((long)(rnd.nextInt(ARTIFICAL_LATENCY))*1000L)+5000L;
                    }
                    //log.info("Next milestone check in {} seconds",latency/1000L);
                    
                    Thread.sleep(latency);
                    
                } catch (final Exception e) {
                    log.error("Error during TipsManager Milestone updating", e);
                }
            }
        }, "Latest Milestone Tracker")).start();
    }

    static Hash transactionToApprove(final Hash extraTip, int depth) {

        long startTime = System.nanoTime();
                
        final Hash preferableMilestone = Milestone.latestSolidSubtangleMilestone;
        
        final int oldestAcceptableMilestoneIndex = Milestone.latestSolidSubtangleMilestoneIndex - depth;
        
        long criticalArrivalTime = Long.MAX_VALUE;
        
        try {

            for (int idx=oldestAcceptableMilestoneIndex; idx <= Milestone.latestSolidSubtangleMilestoneIndex; idx++) {
                MilestoneSummary milestoneSummary = milestoneSummaryTable.get(idx);
                if ( milestoneSummary != null ) {
                    if (milestoneSummary.arrivalTime != 0) {
                        criticalArrivalTime = milestoneSummary.arrivalTime; 
                        break;
                    }
                }
            }
            if (criticalArrivalTime == 0L ) {
                criticalArrivalTime = System.currentTimeMillis() / 1000 - 120;
            }
            
            System.arraycopy(zeroedAnalyzedTransactionsFlags, 0, analyzedTransactionsFlags, 0, 134217728);

            Map<Hash, Long> state = new HashMap<>(Snapshot.initialState);

            {
                int numberOfAnalyzedTransactions = 0;

                final Queue<Long> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(StorageTransactions
                        .instance().transactionPointer((extraTip == null ? preferableMilestone : extraTip).bytes())));
                Long pointer;
                while ((pointer = nonAnalyzedTransactions.poll()) != null) {
                    if (setAnalyzedTransactionFlag(pointer)) {

                        numberOfAnalyzedTransactions++;

                        //final Transaction transaction = StorageTransactions.instance().loadTransaction(pointer);
                        TransactionSummary transactionSummary = transactionSummaryTable.get(pointer);
                        //if (transaction.type == Storage.PREFILLED_SLOT) {
                        if (transactionSummary == null) {
                            log.info("Did not find {}",pointer);
                            return null;

                        } else {

                            if (transactionSummary.currentIndex == 0) {

                                boolean validBundle = false;

                                Bundle bundle = bundleTable.get(ByteBuffer.wrap(transactionSummary.bundle));
                                if ( bundle == null ) {
                                    Transaction transaction = StorageTransactions.instance().loadTransaction(pointer);                                    
                                    //log.info(bytesToHex(transaction.bundle));
                                    System.arraycopy(transaction.bundle, 0, transactionSummary.bundle, 0, Transaction.BUNDLE_SIZE);
                                    bundle = new Bundle(transactionSummary.bundle);
                                    bundleTable.put(ByteBuffer.wrap(transactionSummary.bundle), bundle);                                        
                                }
                                for (final List<Transaction> bundleTransactions : bundle.getTransactions()) {

                                    if (bundleTransactions.get(0).pointer == pointer) {

                                        validBundle = true;

                                        for (final Transaction bundleTransaction : bundleTransactions) {

                                            if (bundleTransaction.value != 0) {
                                                
                                                final Hash address = new Hash(bundleTransaction.address);
                                                final Long value = state.get(address);
                                                state.put(address, value == null ? bundleTransaction.value : (value + bundleTransaction.value));
                                            }
                                        }

                                        break;
                                    }
                                }

                                if (!validBundle) {
                                    bundleTable.remove(ByteBuffer.wrap(transactionSummary.bundle));
                                    return null;
                                }
                            }
                            if ( transactionSummary.arrivalTime >= criticalArrivalTime ) {
                                if (transactionSummary.trunk_pointer == 0L || transactionSummary.branch_pointer == 0L) {
                                    Transaction transaction = StorageTransactions.instance().loadTransaction(pointer);
                                    transactionSummary.branch_pointer = transaction.branchTransactionPointer;
                                    transactionSummary.trunk_pointer = transaction.trunkTransactionPointer;
                                }
                                nonAnalyzedTransactions.offer(transactionSummary.trunk_pointer);                                
                                nonAnalyzedTransactions.offer(transactionSummary.branch_pointer);
                            }
                                              
                        }
                    }
                }
                
                log.info("Analyzed transactions = " + numberOfAnalyzedTransactions);
                if (extraTip == null) {
                    numberOfConfirmedTransactions = numberOfAnalyzedTransactions;
                }
            }

//            final Iterator<Map.Entry<Hash, Long>> stateIterator = state.entrySet().iterator();
//            while (stateIterator.hasNext()) {

//                final Map.Entry<Hash, Long> entry = stateIterator.next();
//                if (entry.getValue() <= 0) {

//                    if (entry.getValue() < 0) {
//                        log.info("Ledger inconsistency detected");
//                        return null;
//                    }

//                    stateIterator.remove();
//                }
                //////////// --Coo only--
                /*
                 * if (entry.getValue() > 0) {
                 * 
                 * System.out.ln("initialState.put(new Hash(\"" + entry.getKey()
                 * + "\"), " + entry.getValue() + "L);"); }
                 */
                ////////////
//            }

            System.arraycopy(analyzedTransactionsFlags, 0, analyzedTransactionsFlagsCopy, 0, 134217728);
            System.arraycopy(zeroedAnalyzedTransactionsFlags, 0, analyzedTransactionsFlags, 0, 134217728);

            final List<Long> tailsToAnalyze = new LinkedList<>();

            
            long tip = StorageTransactions.instance().transactionPointer(preferableMilestone.bytes());            
            if (extraTip != null) {

                //Transaction transaction = StorageTransactions.instance().loadTransaction(tip);
                TransactionSummary transactionSummary = transactionSummaryTable.get(tip);
                //while (depth-- > 0 && tip != Storage.CELLS_OFFSET - Storage.SUPER_GROUPS_OFFSET) {
                while (depth-- > 0) {
                    //tip = transaction.pointer;                    
                    do {
                        tip = transactionSummary.trunk_pointer;     
                        transactionSummary = transactionSummaryTable.get(transactionSummary.trunk_pointer);
                        //transaction = StorageTransactions.instance().loadTransaction(transaction.trunkTransactionPointer);
                    } while (transactionSummary.currentIndex != 0);
                }
            }
            
            /*
            for (int idx=Milestone.latestSolidSubtangleMilestoneIndex-1; idx <= Milestone.latestSolidSubtangleMilestoneIndex; idx++) {
                MilestoneSummary milestoneSummary = milestoneSummaryTable.get(idx);
                if ( milestoneSummary != null ) {
                    tip = milestoneSummary.pointer; 
                    break;
                }
            }
            if ( tip == 0L ) {
                throw new RuntimeException("No milestone at depth!");
            }
            */
            
            final Queue<Long> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(tip));
            Long pointer;
            final Set<Long> tailsWithoutApprovers = new HashSet<>();
            while ((pointer = nonAnalyzedTransactions.poll()) != null) {

                if (setAnalyzedTransactionFlag(pointer)) {

                    final Transaction transaction = StorageTransactions.instance().loadTransaction(pointer);

                    if (transaction.currentIndex == 0 && !tailsToAnalyze.contains(transaction.pointer)) {

                        tailsToAnalyze.add(transaction.pointer);
                    }

                    final long approveePointer = StorageApprovers.instance().approveePointer(transaction.hash);
                    if (approveePointer == 0) {

                        if (transaction.currentIndex == 0) {

                            tailsWithoutApprovers.add(pointer);
                        }

                    } else {

                        for (final Long approverPointer : StorageApprovers.instance().approveeTransactions(approveePointer)) {
                            nonAnalyzedTransactions.offer(approverPointer);
                        }
                    }
                }
            }
            tailsToAnalyze.removeAll(tailsWithoutApprovers); // Remove them from where they are...
            tailsToAnalyze.addAll(tailsWithoutApprovers);    // ...and add to the very end

            if (extraTip != null) {

                System.arraycopy(analyzedTransactionsFlagsCopy, 0, analyzedTransactionsFlags, 0, 134217728);

                final Iterator<Long> tailsToAnalyzeIterator = tailsToAnalyze.iterator();
                while (tailsToAnalyzeIterator.hasNext()) {

                    final Long tailPointer = tailsToAnalyzeIterator.next();
                    if ((analyzedTransactionsFlags[(int) ((tailPointer
                            - (Storage.CELLS_OFFSET - Storage.SUPER_GROUPS_OFFSET)) >> (11 + 3))]
                            & (1 << (((tailPointer - (Storage.CELLS_OFFSET - Storage.SUPER_GROUPS_OFFSET)) >> 11)
                                    & 7))) != 0) {

                        tailsToAnalyzeIterator.remove();
                    }
                }
            }

            log.info(tailsToAnalyze.size() + " tails need to be analyzed");

            /* --Coo only-- Hash bestTip = preferableMilestone; */
            int bestRating = 0;
            // final Set<Long> seenTails = new HashSet<>();

            /**/final Map<Hash, Integer> tailsRaitings = new HashMap<>();

            for (int i = tailsToAnalyze.size(); i-- > 0;) {

                final Long tailPointer = tailsToAnalyze.get(i);
                /*
                 * -- Coo only-- if (seenTails.contains(tailPointer)) {
                 * 
                 * continue; }
                 */

                System.arraycopy(analyzedTransactionsFlagsCopy, 0, analyzedTransactionsFlags, 0, 134217728);

                final Set<Long> extraTransactions = new HashSet<>();

                nonAnalyzedTransactions.clear();
                nonAnalyzedTransactions.offer(tailPointer);
                while ((pointer = nonAnalyzedTransactions.poll()) != null) {

                    if (setAnalyzedTransactionFlag(pointer)) {

                        final Transaction transaction = StorageTransactions.instance().loadTransaction(pointer);
                        if (transaction.type == Storage.PREFILLED_SLOT) {

                            // -- Coo only--
                            // seenTails.addAll(extraTransactions);

                            extraTransactions.clear();

                            break;

                        } else {

                            extraTransactions.add(pointer);

                            nonAnalyzedTransactions.offer(transaction.trunkTransactionPointer);
                            nonAnalyzedTransactions.offer(transaction.branchTransactionPointer);
                        }
                    }
                }

                if (extraTransactions.size() > /* bestRating */0) {

                    Set<Long> extraTransactionsCopy = new HashSet<>(extraTransactions);

                    for (final Long extraTransactionPointer : extraTransactions) {

                        final Transaction transaction = StorageTransactions.instance()
                                .loadTransaction(extraTransactionPointer);
                        if (transaction.currentIndex == 0) {

                            Bundle bundle = bundleTable.get(ByteBuffer.wrap(transaction.bundle));
                            if ( bundle == null ) {
                                bundle = new Bundle(transaction.bundle);
                                bundleTable.put(ByteBuffer.wrap(transaction.bundle), bundle);                                    
                            }
                            for (final List<Transaction> bundleTransactions : bundle.getTransactions()) {

                                if (bundleTransactions.get(0).pointer == transaction.pointer) {

                                    for (final Transaction bundleTransaction : bundleTransactions) {
                                        
                                        long itsArrivalTime = bundleTransaction.arrivalTime;
                                        if (itsArrivalTime == 0)
                                            itsArrivalTime = (int) Converter.longValue(bundleTransaction.trits(), Transaction.TIMESTAMP_TRINARY_OFFSET, 27);

                                        if (itsArrivalTime < criticalArrivalTime) {
                                            extraTransactionsCopy = null;
                                            break;
                                        }

                                        if (!extraTransactionsCopy.remove(bundleTransaction.pointer)) {
                                            extraTransactionsCopy = null;
                                            break;
                                        }
                                    }
                                    break;
                                }
                            }
                        }

                        if (extraTransactionsCopy == null) {

                            break;
                        }
                    }

                    if (extraTransactionsCopy != null && extraTransactionsCopy.isEmpty()) {

                        final Map<Hash, Long> stateCopy = new HashMap<>(state);

                        for (final Long extraTransactionPointer : extraTransactions) {

                            final Transaction transaction = StorageTransactions.instance()
                                    .loadTransaction(extraTransactionPointer);
                            if (transaction.value != 0) {

                                final Hash address = new Hash(transaction.address);
                                final Long value = stateCopy.get(address);
                                stateCopy.put(address, value == null ? transaction.value : (value + transaction.value));
                            }
                        }

                        for (final long value : stateCopy.values()) {

                            if (value < 0) {

                                extraTransactions.clear();

                                break;
                            }
                        }

                        if (!extraTransactions.isEmpty()) {

                            // --Coo only--
                            // bestTip = new Hash(Storage.loadTransaction(tailPointer).hash, 0, Transaction.HASH_SIZE);
                            // bestRating = extraTransactions.size();
                            // seenTails.addAll(extraTransactions);

                            /**/tailsRaitings
                                    .put(new Hash(StorageTransactions.instance().loadTransaction(tailPointer).hash, 0,
                                            Transaction.HASH_SIZE), extraTransactions.size());
                            /**/if (extraTransactions.size() > bestRating) {
                                /**/
                                /**/bestRating = extraTransactions.size();
                                /**/}
                        }
                    }
                }
            }
            // System.out.ln(bestRating + " extra transactions approved");

            /**/if (tailsRaitings.isEmpty()) {
                /**/if (extraTip == null) {
                    /**/ return preferableMilestone;
                    /**/}
                /**/}

            /**/final Map<Hash, Integer> filteredTailsRatings = new HashMap<>();
            /**/long totalSquaredRating = 0;
            /**/for (final Map.Entry<Hash, Integer> entry : tailsRaitings.entrySet()) {
                /**/
                /**/if (entry.getValue() >= bestRating * RATING_THRESHOLD / 100) {
                    /**/
                    /**/filteredTailsRatings.put(entry.getKey(), entry.getValue());
                    /**/totalSquaredRating += ((long) entry.getValue()) * entry.getValue();
                    /**/}
                /**/}
            /**/if (totalSquaredRating > 0L) {
                /**/long hit = java.util.concurrent.ThreadLocalRandom.current().nextLong(totalSquaredRating);
                /**/for (final Map.Entry<Hash, Integer> entry : filteredTailsRatings.entrySet()) {
                    /**/
                    /**/if ((hit -= ((long) entry.getValue()) * entry.getValue()) < 0) {
                        /**/
                        /**/log.info(entry.getValue() + "/" + bestRating + " extra transactions approved");
                        /**/return entry.getKey();
                        /**/}
                    /**/}
                /**/}
            /**/else {
                /**/return preferableMilestone;
                /**/}

            /**/throw new RuntimeException("Must never be reached!");
            // return bestTip;

        } finally {
            API.incEllapsedTime_getTxToApprove(System.nanoTime() - startTime);
        }
    }

    private static boolean setAnalyzedTransactionFlag(long pointer) {

        pointer -= Storage.CELLS_OFFSET - Storage.SUPER_GROUPS_OFFSET;

        final int value = analyzedTransactionsFlags[(int) (pointer >> (11 + 3))];
        if ((value & (1 << ((pointer >> 11) & 7))) == 0) {
            analyzedTransactionsFlags[(int) (pointer >> (11 + 3))] = (byte) (value | (1 << ((pointer >> 11) & 7)));
            return true;
        } else {
            return false;
        }
    }
    
    public void shutDown() {
        shuttingDown = true;
    }
    
    public static TipsManager instance() {
        return instance;
    }
    
    private TipsManager() {}
    
    private static TipsManager instance = new TipsManager();
}

