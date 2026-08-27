package com.carddemo.batch;

import com.carddemo.domain.Account;
import com.carddemo.domain.CardXref;
import com.carddemo.domain.Customer;
import com.carddemo.domain.Transaction;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.repeat.RepeatStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

final class StatementTasklet implements Tasklet {

    private final StatementDataService data;
    private final FlatFileItemWriter<String> textWriter;
    private final FlatFileItemWriter<String> htmlWriter;

    StatementTasklet(
            StatementDataService data,
            FlatFileItemWriter<String> textWriter,
            FlatFileItemWriter<String> htmlWriter) {
        this.data = data;
        this.textWriter = textWriter;
        this.htmlWriter = htmlWriter;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext context)
            throws Exception {
        var executionContext = context.getStepContext()
                .getStepExecution()
                .getExecutionContext();
        textWriter.open(executionContext);
        htmlWriter.open(executionContext);
        List<String> text = new ArrayList<>();
        List<String> html = new ArrayList<>();
        html.add("<!DOCTYPE html>");
        html.add("<html lang=\"en\">");
        html.add("<head>");
        html.add("<meta charset=\"utf-8\">");
        html.add("<title>HTML Table Layout</title>");
        html.add("</head>");
        html.add("<body style=\"margin:0px;\">");
        html.add("<table  align=\"center\" frame=\"box\" "
                + "style=\"width:70%; font:12px Segoe UI,sans-serif;\">");
        data.open();
        for (CardXref xref : data.readXrefs()) {
            Account account = data.readAccount(xref.getAccountId()).orElse(null);
            Customer customer = data.readCustomer(xref.getCustomerId()).orElse(null);
            if (account == null || customer == null) {
                continue;
            }
            List<Transaction> transactions = data.readTransactions(xref.getCardNumber());
            BigDecimal total = BigDecimal.ZERO.setScale(2);
            text.add("********************************START OF STATEMENT********************************");
            text.add("Statement for Account Number: " + account.getAcctId());
            text.add(String.format("Account ID         : %d", account.getAcctId()));
            text.add("Current Balance    : "
                    + ReportSupport.money(account.getCurrentBalance()));
            text.add("FICO Score         : " + customer.getFicoCreditScore());
            text.add("Basic Details");
            text.add(customer.getFirstName() + " " + customer.getLastName());
            text.add(customer.getAddressLine1());
            text.add(customer.getAddressLine2());
            text.add(customer.getAddressLine3());
            text.add("Current Balance: " + ReportSupport.money(account.getCurrentBalance()));
            text.add("FICO Score: " + customer.getFicoCreditScore());
            text.add("Transaction Summary");
            text.add("Tran ID         Tran Details                                      Tran Amount");
            html.add("<tr>");
            html.add("<td colspan=\"3\" style=\"padding:0px 5px; "
                    + "background-color:#1d1d96b3;\">");
            html.add("<h3>Statement for Account Number: " + account.getAcctId() + "</h3>");
            html.add("</td>");
            html.add("</tr>");
            html.add("<p style=\"font-size:16px\">Basic Details</p>");
            html.add("<p>Account ID: " + account.getAcctId() + "</p>");
            html.add("<p>" + customer.getFirstName() + " " + customer.getLastName() + "</p>");
            html.add("<p>Current Balance: " + ReportSupport.money(account.getCurrentBalance()) + "</p>");
            html.add("<p>FICO Score: " + customer.getFicoCreditScore() + "</p>");
            html.add("<p style=\"font-size:16px\">Transaction Summary</p>");
            for (Transaction transaction : transactions) {
                text.add(String.format(
                        "%-16s %-49s $%10s",
                        transaction.getId(),
                        transaction.getDescription(),
                        ReportSupport.money(transaction.getAmount())));
                html.add("<tr>");
                html.add("<td>" + transaction.getId() + "</td>");
                html.add("<td>" + transaction.getDescription() + "</td>");
                html.add("<td>" + ReportSupport.money(transaction.getAmount()) + "</td>");
                html.add("</tr>");
                total = total.add(transaction.getAmount());
            }
            text.add(String.format("Total EXP:%64s$%10s", "", ReportSupport.money(total)));
            text.add("********************************END OF STATEMENT********************************");
            html.add("<h3>Total EXP: " + ReportSupport.money(total) + "</h3>");
            html.add("<h3>End of Statement</h3>");
        }
        data.close();
        html.add("</table>");
        html.add("</body>");
        html.add("</html>");
        textWriter.write(new Chunk<>(text));
        htmlWriter.write(new Chunk<>(html));
        textWriter.close();
        htmlWriter.close();
        contribution.incrementWriteCount(text.size() + html.size());
        return RepeatStatus.FINISHED;
    }
}
