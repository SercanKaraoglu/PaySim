package paysim.aggregation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.stream.Collectors;

import paysim.Transaction;
import paysim.parameters.Parameters;
import paysim.parameters.TransactionParameters;

public class AggregateParamFileCreator {
    private static int DOUBLE_PRECISION = 2;
    String MONTH_TEN = "10";

    public ArrayList<AggregateTransactionRecord> generateAggregateParamFile(ArrayList<Transaction> transactionList) {
        ArrayList<AggregateTransactionRecord> aggrTransRecord = new ArrayList<>();
        for (String type : TransactionParameters.getActions()) {
            for (int step = 1; step <= Parameters.nbSteps; step++) {
                AggregateTransactionRecord partialRecord = getAggregateRecord(type, step, transactionList);
                if (partialRecord != null) {
                    aggrTransRecord.add(partialRecord);
                }
            }
        }
        java.util.Collections.sort(aggrTransRecord);
        return aggrTransRecord;
    }

    /**
     * Using a fast version of getAggregateRecord
     * key property : already sorted Aggregate
     * more appropriate name "aggregate" ?
     */
    public ArrayList<AggregateTransactionRecord> reformat(ArrayList<AggregateTransactionRecord> list) {
        ArrayList<AggregateTransactionRecord> reformedList = new ArrayList<>();
        ArrayList<AggregateTransactionRecord> tempList;

        for (int i = 0; i < list.size(); i++) {
            tempList = new ArrayList<>();
            AggregateTransactionRecord temp = list.get(i);
            tempList.add(temp);
            int counter = i + 1;

            //Get all records looking alike temp
            while (counter < list.size()) {
                AggregateTransactionRecord toCheckRecord = list.get(counter);
                if (temp.equals(toCheckRecord)) {
                    tempList.add(toCheckRecord);
                    counter++;
                } else {
                    break;
                }
            }
            i = counter - 1;
            AggregateTransactionRecord compacted = compactAggrRecord(tempList);
            reformedList.add(compacted);
        }

        return reformedList;
    }

    private AggregateTransactionRecord compactAggrRecord(ArrayList<AggregateTransactionRecord> recordList) {
        String type = recordList.get(0).getType();
        String month = recordList.get(0).getMonth();
        String day = recordList.get(0).gettDay();
        String hour = recordList.get(0).gettHour();
        String step = recordList.get(0).gettStep();

        double totalCount = recordList.stream()
                .map(AggregateTransactionRecord::gettCount)
                .mapToDouble(Double::parseDouble)
                .sum();

        double sum = recordList.stream()
                .map(AggregateTransactionRecord::gettSum)
                .mapToDouble(Double::parseDouble)
                .sum();


        double std = recordList.stream()
                .map(AggregateTransactionRecord::gettStd)
                .mapToDouble(Double::parseDouble)
                .sum();
        std = std / ((double) recordList.size());
        double avg = sum / totalCount;

        AggregateTransactionRecord compacted =
                new AggregateTransactionRecord(type,
                        month, day, hour,
                        String.valueOf(totalCount),
                        String.valueOf(sum),
                        String.valueOf(avg),
                        String.valueOf(std),
                        step);

        return compacted;
    }

    public AggregateTransactionRecord getAggregateRecord(String type, int step, ArrayList<Transaction> transactionList) {
        ArrayList<Transaction> subsetTransList = transactionList.stream()
                .filter(t -> t.getStep() == step && t.getType().equals(type))
                .collect(Collectors.toCollection(ArrayList::new));

        if (subsetTransList.size() > 0) {
            double sum = getTotalAmount(subsetTransList);
            int count = subsetTransList.size();
            double average = getTruncatedDouble(sum / (double) count);
            double tstd = getTruncatedDouble(getStd(subsetTransList, average));

            // We count days starting from 1 and hours starting from 0
            int day = step / 24 + 1;
            int hour = step % 24;

            AggregateTransactionRecord recordToReturn = new AggregateTransactionRecord(String.valueOf(type),
                    MONTH_TEN,
                    String.valueOf(day),
                    String.valueOf(hour),
                    String.valueOf(count),
                    String.valueOf(sum),
                    String.valueOf(average),
                    String.valueOf(tstd),
                    String.valueOf(step));
            return recordToReturn;
        } else {
            return null;
        }

    }

    public static double getStd(ArrayList<Transaction> list, double average) {
        // Bessel corrected deviation https://en.wikipedia.org/wiki/Bessel%27s_correction
        return Math.sqrt(list.stream()
                .map(Transaction::getAmount)
                .map(val -> val - average)
                .mapToDouble(val -> Math.pow(val, 2))
                .sum())
                / (list.size() - 1);
    }

    private double getTotalAmount(ArrayList<Transaction> transactionList) {
        return transactionList.stream()
                .mapToDouble(Transaction::getAmount)
                .sum();
    }

    private double getTruncatedDouble(double d) {
        try {
            return new BigDecimal(d)
                    .setScale(DOUBLE_PRECISION, BigDecimal.ROUND_HALF_UP)
                    .doubleValue();
        } catch (Exception e) {
            return 0;
        }
    }
}