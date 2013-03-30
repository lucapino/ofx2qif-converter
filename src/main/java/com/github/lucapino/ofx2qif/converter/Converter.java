/*
 *    Copyright (c) 2013 Luca Tagliani
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package com.github.lucapino.ofx2qif.converter;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;
import net.sf.ofx4j.io.DefaultHandler;
import net.sf.ofx4j.io.nanoxml.NanoXMLOFXReader;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class implements the conversion algorithm
 *
 * @author tagliani
 */
public class Converter {

    /**
     * The standard slf4j logger
     */
    static Logger logger = LoggerFactory.getLogger(Converter.class);
    /**
     * The transaction list.
     */
    List<Transaction> transactions;

    /**
     * Converts the OFX file to a QIF file
     *
     * @param inFile the input OFX file.
     * @param outFile the output QIF file.
     * @throws Exception in case of error
     */
    public void convert(String inFile, String outFile) throws Exception {
        NanoXMLOFXReader reader = new NanoXMLOFXReader();
        final Map<String, String> headers = new HashMap<>();
        final Stack<Map<String, Object>> aggregateStack = new Stack<>();
        TreeMap<String, Object> root = new TreeMap<>();
        aggregateStack.push(root);

        // initialize the transaction list
        transactions = new ArrayList<>();
        reader.setContentHandler(getNewDefaultHandler(headers, aggregateStack));
        // parse the OFX and fill the transaction list
        reader.parse(new FileInputStream(inFile));

        // write the QIF file
        FileOutputStream fos = new FileOutputStream(outFile);
        IOUtils.write("\n", fos);
        IOUtils.write("!Type:Bank\n", fos);
        IOUtils.write("\n", fos);
        System.out.println("=================================");
        for (int i = 0; i < transactions.size();) {
            Transaction transaction = transactions.get(i);
            i++;
            System.out.println("Transaction " + i);
            System.out.println("=================================");
            System.out.println("D" + transaction.getDate());
            System.out.println("T" + transaction.getAmount());
            System.out.println("M" + transaction.getMemo());
            IOUtils.write("D" + transaction.getDate() + "\n", fos);
            IOUtils.write("T" + transaction.getAmount() + "\n", fos);
            IOUtils.write("M" + transaction.getMemo() + "\n^\n\n", fos);
            System.out.println("=================================");
        }
    }

    private DefaultHandler getNewDefaultHandler(final Map<String, String> headers, final Stack<Map<String, Object>> aggregateStack) {
        return new DefaultHandler() {
            Transaction currentTransaction;

            @Override
            public void onHeader(String name, String value) {
                logger.debug(name + ":" + value);
                headers.put(name, value);
            }

            @Override
            public void onElement(String name, String value) {
                logger.debug("onElement " + aggregateStack.size());
                if (currentTransaction != null) {
                    switch (name.toLowerCase()) {
                        case "trntype":
                            currentTransaction.setType(value.toUpperCase());
                            break;
                        case "dtuser":
                            try {
                                SimpleDateFormat inSdf = new SimpleDateFormat("yyyyMMdd");
                                SimpleDateFormat outSdf = new SimpleDateFormat("dd/MM/yyyy");
                                currentTransaction.setDate(outSdf.format(inSdf.parse(value)).toUpperCase());
                            } catch (Exception ex) {
                            }
                            break;
                        case "trnamt":
                            // 1,024.00
                            // 1.024,00
                            // replace all the , with .
                            String amount = value.replaceAll(",", ".").toUpperCase();
                            // 1.024.00 both
                            // replace the last . with ,
                            StringBuilder b = new StringBuilder(amount);
                            b.replace(amount.lastIndexOf("."), amount.lastIndexOf(".") + 1, ",");
                            // 1.024,00
                            currentTransaction.setAmount(b.toString());
                            break;
                        case "name":
                            String trimmedString = value.trim().replaceAll("\\s+", " ").replaceAll("\\.\\s", ".");
                            currentTransaction.setMemo((trimmedString.substring(0, trimmedString.length() - 1) + ": ").toUpperCase());
                            break;
                        case "memo":
                            currentTransaction.setMemo(currentTransaction.getMemo() + value.trim().replaceAll("\\s+", " ").replaceAll("\\.\\s", ".").toUpperCase());
                    }
                }
                char[] tabs = new char[aggregateStack.size() * 2];
                Arrays.fill(tabs, ' ');
                logger.debug(new String(tabs) + name + "=" + value);
                aggregateStack.peek().put(name, value);
            }

            @Override
            public void startAggregate(String aggregateName) {
                logger.debug("startAggregate " + aggregateName + " " + aggregateStack.size());
                if (aggregateName.equalsIgnoreCase("stmttrn")) {
                    // find a new transaction start
                    // let's create a new transaction object
                    currentTransaction = new Transaction();
                }
                char[] tabs = new char[aggregateStack.size() * 2];
                Arrays.fill(tabs, ' ');
                logger.debug(new String(tabs) + aggregateName + " {");

                TreeMap<String, Object> aggregate = new TreeMap<>();
                aggregateStack.peek().put(aggregateName, aggregate);
                aggregateStack.push(aggregate);
            }

            @Override
            public void endAggregate(String aggregateName) {
                logger.debug("endAggregate " + aggregateName + " " + aggregateStack.size());
                if (aggregateName.equalsIgnoreCase("stmttrn")) {
                    // find end of transaction
                    // let's add the current transaction to the transactions list
                    transactions.add(currentTransaction);
                    currentTransaction = null;
                }
                aggregateStack.pop();

                char[] tabs = new char[aggregateStack.size() * 2];
                Arrays.fill(tabs, ' ');
                logger.debug(new String(tabs) + "}");
            }
        };
    }
}
